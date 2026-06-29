/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import io.vertx.core.Future;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small Caffeine-like asynchronous loading cache backed by Vert.x {@link Future} and
 * {@link ConcurrentHashMap}.
 *
 * <p>Caffeine cannot run on Android: 3.x initializes a {@link java.lang.System.Logger} (a JDK API
 * Android does not provide and core-library desugaring does not cover), while 2.x reads the
 * OpenJDK-internal {@code Thread.threadLocalRandomProbe} field through {@code sun.misc.Unsafe}, which
 * does not exist on Android.
 *
 * <p>This implementation intentionally keeps the policy simple: entries expire lazily on reads,
 * writes and {@link #cleanUp()}, and size eviction scans for the least recently accessed entry when
 * the map grows beyond {@code maximumSize}. Concurrent loads for the same key are coalesced.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class AsyncCache<K, V> {
	private static final Logger log = LoggerFactory.getLogger(AsyncCache.class);

	/** Loads a value using Vert.x futures. */
	@FunctionalInterface
	public interface AsyncLoader<K, V> {
		Future<V> load(K key);
	}

	/** Receives notifications when a cached entry is removed. */
	@FunctionalInterface
	public interface RemovalListener<K, V> {
		void onRemoval(K key, @Nullable V value, RemovalCause cause);
	}

	public enum RemovalCause {
		EXPLICIT,
		REPLACED,
		EXPIRED,
		SIZE
	}

	private static final long NO_EXPIRATION = Long.MAX_VALUE;
	private final long maximumSize;
	private final long expireAfterAccessNanos;
	private final long expireAfterWriteNanos;
	private final AsyncLoader<K, V> loader;
	private final RemovalListener<? super K, ? super V> removalListener;
	private final ConcurrentHashMap<K, CacheEntry<V>> store;
	private final Sync sync = new Sync();

	private AsyncCache(Builder<K, V> builder, AsyncLoader<K, V> loader) {
		this.maximumSize = builder.maximumSize;
		this.expireAfterAccessNanos = builder.expireAfterAccessNanos;
		this.expireAfterWriteNanos = builder.expireAfterWriteNanos;
		this.loader = Objects.requireNonNull(loader, "loader");
		this.removalListener = builder.removalListener;
		this.store = new ConcurrentHashMap<>(builder.initialCapacity);
	}

	public static <K, V> Builder<K, V> newBuilder() {
		return new Builder<>();
	}

	/**
	 * Returns the cached value future for {@code key}, loading it if absent or expired. This mirrors
	 * Caffeine's {@code AsyncLoadingCache.get} return type for existing client code.
	 */
	public Future<V> get(K key) {
		return getEntry(key).future;
	}

	private CacheEntry<V> getEntry(K key) {
		Objects.requireNonNull(key, "key");
		long now = System.nanoTime();
		Load<K, V> load = new Load<>();

		CacheEntry<V> entry = store.compute(key, (k, current) -> {
			if (isReusable(current, now)) {
				current.lastAccessNanos = now;
				return current;
			}

			if (current != null && isCompletePresent(current) && isExpired(current, now))
				load.removed = new Removal<>(k, current, RemovalCause.EXPIRED);

			load.loaded = true;
			CacheEntry<V> created = new CacheEntry<>(loader.load(k), now);
			load.entry = created;
			return created;
		});

		notifyRemoval(load.removed);
		if (load.loaded && load.entry != null) {
			CacheEntry<V> loadedEntry = load.entry;
			loadedEntry.future.onComplete(ar -> {
				if (ar.failed() || ar.result() == null)
					store.remove(key, loadedEntry);
			});
			evictIfNeeded();
		}

		return entry;
	}

	public Sync synchronous() {
		return sync;
	}

	public void cleanUp() {
		long now = System.nanoTime();
		List<Removal<K, V>> removals = new ArrayList<>();
		for (Map.Entry<K, CacheEntry<V>> e : store.entrySet()) {
			CacheEntry<V> entry = e.getValue();
			if (isCompletePresent(entry) && isExpired(entry, now) && store.remove(e.getKey(), entry))
				removals.add(new Removal<>(e.getKey(), entry, RemovalCause.EXPIRED));
		}
		notifyRemovals(removals);
		evictIfNeeded();
	}

	private boolean isReusable(@Nullable CacheEntry<V> entry, long now) {
		if (entry == null)
			return false;

		Future<V> future = entry.future;
		if (!future.isComplete())
			return true;

		return isCompletePresent(entry) && !isExpired(entry, now);
	}

	private boolean isCompletePresent(CacheEntry<V> entry) {
		Future<V> future = entry.future;
		return future.isComplete() && future.succeeded() && future.result() != null;
	}

	private boolean isExpired(CacheEntry<V> entry, long now) {
		return elapsed(now, entry.lastAccessNanos) > expireAfterAccessNanos ||
				elapsed(now, entry.writeNanos) > expireAfterWriteNanos;
	}

	private long elapsed(long now, long then) {
		long elapsed = now - then;
		return elapsed < 0 ? Long.MAX_VALUE : elapsed;
	}

	private @Nullable V presentValue(@Nullable CacheEntry<V> entry, long now) {
		if (entry == null || !isCompletePresent(entry) || isExpired(entry, now))
			return null;

		return entry.future.result();
	}

	private void evictIfNeeded() {
		List<Removal<K, V>> removals = new ArrayList<>();
		while (store.size() > maximumSize) {
			Map.@Nullable Entry<K, CacheEntry<V>> oldest = null;
			for (Map.Entry<K, CacheEntry<V>> e : store.entrySet()) {
				if (oldest == null || e.getValue().lastAccessNanos < oldest.getValue().lastAccessNanos)
					oldest = e;
			}
			if (oldest == null)
				break;

			if (store.remove(oldest.getKey(), oldest.getValue()))
				removals.add(new Removal<>(oldest.getKey(), oldest.getValue(), RemovalCause.SIZE));
		}
		notifyRemovals(removals);
	}

	private void notifyRemoval(@Nullable Removal<K, V> removal) {
		if (removal == null)
			return;

		V value = valueForNotification(removal.entry);
		try {
			removalListener.onRemoval(removal.key, value, removal.cause);
		} catch (Throwable t) {
			// Isolate listener failures: a misbehaving listener must not break cache reads,
			// cleanup, compute or abort the rest of a removal batch.
			log.warn("Removal listener threw for key {} (cause {})", removal.key, removal.cause, t);
		}
	}

	private void notifyRemovals(List<Removal<K, V>> removals) {
		for (Removal<K, V> removal : removals)
			notifyRemoval(removal);
	}

	private @Nullable V valueForNotification(CacheEntry<V> entry) {
		return isCompletePresent(entry) ? entry.future.result() : null;
	}

	/** Builder for a Caffeine-like cache configuration. */
	public static final class Builder<K, V> {
		private int initialCapacity = 16;
		private long maximumSize = Long.MAX_VALUE;
		private long expireAfterAccessNanos = NO_EXPIRATION;
		private long expireAfterWriteNanos = NO_EXPIRATION;
		private RemovalListener<? super K, ? super V> removalListener = (key, value, cause) -> {
		};

		public Builder<K, V> initialCapacity(int initialCapacity) {
			if (initialCapacity < 0)
				throw new IllegalArgumentException("initialCapacity must not be negative");
			this.initialCapacity = initialCapacity;
			return this;
		}

		public Builder<K, V> maximumSize(long maximumSize) {
			if (maximumSize < 0)
				throw new IllegalArgumentException("maximumSize must not be negative");
			this.maximumSize = maximumSize;
			return this;
		}

		public Builder<K, V> expireAfterAccess(Duration duration) {
			this.expireAfterAccessNanos = toNanos(duration, "expireAfterAccess");
			return this;
		}

		public Builder<K, V> expireAfterWrite(Duration duration) {
			this.expireAfterWriteNanos = toNanos(duration, "expireAfterWrite");
			return this;
		}

		public Builder<K, V> removalListener(RemovalListener<? super K, ? super V> removalListener) {
			this.removalListener = Objects.requireNonNull(removalListener, "removalListener");
			return this;
		}

		public AsyncCache<K, V> build(AsyncLoader<K, V> loader) {
			return new AsyncCache<>(this, loader);
		}

		private static long toNanos(Duration duration, String name) {
			Objects.requireNonNull(duration, name);
			if (duration.isNegative())
				throw new IllegalArgumentException(name + " must not be negative");
			long nanos = duration.toNanos();
			return nanos < 0 ? Long.MAX_VALUE : nanos;
		}
	}

	/** The synchronous view, mirroring the subset of Caffeine's {@code synchronous()} API in use. */
	public final class Sync {
		private final MapView mapView = new MapView();

		public MapView asMap() {
			return mapView;
		}

		public @Nullable V getIfPresent(K key) {
			Objects.requireNonNull(key, "key");
			long now = System.nanoTime();
			Removal<K, V>[] removal = removalBox();
			CacheEntry<V> entry = store.computeIfPresent(key, (k, current) -> {
				if (!current.future.isComplete())
					return current;

				V value = presentValue(current, now);
				if (value == null) {
					if (isCompletePresent(current) && isExpired(current, now))
						removal[0] = new Removal<>(k, current, RemovalCause.EXPIRED);
					return null;
				}
				current.lastAccessNanos = now;
				return current;
			});
			notifyRemoval(removal[0]);

			return entry == null ? null : entry.future.result();
		}

		public void invalidate(K key) {
			Objects.requireNonNull(key, "key");
			CacheEntry<V> removed = store.remove(key);
			if (removed != null)
				notifyRemoval(new Removal<>(key, removed, RemovalCause.EXPLICIT));
		}

		public void invalidateAll(Iterable<? extends K> keys) {
			Objects.requireNonNull(keys, "keys");
			for (K key : keys)
				invalidate(key);
		}

		public void invalidateAll() {
			List<Removal<K, V>> removals = new ArrayList<>();
			for (Map.Entry<K, CacheEntry<V>> e : store.entrySet()) {
				if (store.remove(e.getKey(), e.getValue()))
					removals.add(new Removal<>(e.getKey(), e.getValue(), RemovalCause.EXPLICIT));
			}
			notifyRemovals(removals);
		}
	}

	/** The minimal map view used by the client: only {@code compute} for value insertion/replacement. */
	public final class MapView {
		public @Nullable V compute(K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> remappingFunction) {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(remappingFunction, "remappingFunction");
			long now = System.nanoTime();
			Removal<K, V>[] removal = removalBox();

			CacheEntry<V> entry = store.compute(key, (k, current) -> {
				V currentValue = presentValue(current, now);
				V newValue = remappingFunction.apply(k, currentValue);
				if (newValue == null) {
					if (current != null)
						removal[0] = new Removal<>(k, current, currentValue == null &&
								isCompletePresent(current) && isExpired(current, now) ?
								RemovalCause.EXPIRED : RemovalCause.EXPLICIT);
					return null;
				}

				if (current != null)
					removal[0] = new Removal<>(k, current, currentValue == null &&
							isCompletePresent(current) && isExpired(current, now) ?
							RemovalCause.EXPIRED : RemovalCause.REPLACED);
				return new CacheEntry<>(Future.succeededFuture(newValue), now);
			});

			notifyRemoval(removal[0]);
			evictIfNeeded();
			return entry == null ? null : entry.future.result();
		}
	}

	@SuppressWarnings("unchecked")
	private Removal<K, V>[] removalBox() {
		return (Removal<K, V>[]) new Removal<?, ?>[1];
	}

	private static final class CacheEntry<V> {
		final Future<V> future;
		final long writeNanos;
		volatile long lastAccessNanos;

		CacheEntry(Future<V> future, long now) {
			this.future = Objects.requireNonNull(future, "future");
			this.writeNanos = now;
			this.lastAccessNanos = now;
		}
	}

	private static final class Load<K, V> {
		boolean loaded;
		@Nullable CacheEntry<V> entry;
		@Nullable Removal<K, V> removed;
	}

	private static final class Removal<K, V> {
		final K key;
		final CacheEntry<V> entry;
		final RemovalCause cause;

		Removal(K key, CacheEntry<V> entry, RemovalCause cause) {
			this.key = key;
			this.entry = entry;
			this.cause = cause;
		}
	}
}