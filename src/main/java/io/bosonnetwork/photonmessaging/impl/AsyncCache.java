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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

/**
 * Minimal asynchronous loading cache used by {@link PhotonMessagingClient} in place of Caffeine.
 *
 * <p>Caffeine cannot run on Android: 3.x initialises a {@link java.lang.System.Logger} (a JDK API
 * Android does not provide and core-library desugaring does not cover), while 2.x reads the
 * OpenJDK-internal {@code Thread.threadLocalRandomProbe} field through {@code sun.misc.Unsafe}, which
 * does not exist on Android. The desktop/server layer keeps using Caffeine (via {@code VertxCaffeine});
 * only the messaging client's two small caches are served by this class so the client runs on both.
 *
 * <p>This implements only the small surface the client needs: asynchronous {@link #get(Object) loading}
 * and a synchronous view exposing value insertion ({@code asMap().compute}), point lookup and
 * invalidation. Eviction is best-effort: entries expire lazily on access after {@code expireAfterAccess},
 * and the map is bounded to {@code maximumSize} by evicting the least-recently-accessed entry on insert.
 * Backed by a {@link ConcurrentHashMap}; all per-key mutations are atomic.
 *
 * @param <K> the key type
 * @param <V> the (non-null) value type
 */
final class AsyncCache<K, V> {
	/**
	 * Loads a value for a key. Mirrors Caffeine's async cache loader: the returned future completing
	 * with {@code null} (or exceptionally) means "no value", and such entries are not retained.
	 */
	@FunctionalInterface
	interface AsyncLoader<K, V> {
		CompletableFuture<V> load(K key, Executor executor);
	}

	// The loader supplies Vert.x futures already bound to the right context, so the executor is unused.
	private static final Executor DIRECT_EXECUTOR = Runnable::run;

	private final long maximumSize;
	private final long expireAfterAccessNanos;
	private final AsyncLoader<K, V> loader;
	private final ConcurrentHashMap<K, Entry<V>> store = new ConcurrentHashMap<>();
	private final Sync sync = new Sync();

	AsyncCache(long maximumSize, Duration expireAfterAccess, AsyncLoader<K, V> loader) {
		this.maximumSize = maximumSize;
		this.expireAfterAccessNanos = expireAfterAccess.toNanos();
		this.loader = loader;
	}

	/**
	 * Returns the cached value future for {@code key}, loading it if absent or expired. Concurrent
	 * loads for the same key are coalesced onto a single future.
	 */
	CompletableFuture<V> get(K key) {
		long now = System.nanoTime();
		boolean[] loaded = {false};
		Entry<V> entry = store.compute(key, (k, current) -> {
			if (current != null && isReusable(current, now)) {
				current.lastAccessNanos = now;
				return current;
			}
			loaded[0] = true;
			return new Entry<>(loader.load(k, DIRECT_EXECUTOR), now);
		});

		if (loaded[0]) {
			// Attached outside compute() so the cleanup never re-enters the map under its own key lock.
			entry.future.whenComplete((value, error) -> {
				if (error != null || value == null)
					store.remove(key, entry);
			});
			evictIfNeeded();
		}
		return entry.future;
	}

	Sync synchronous() {
		return sync;
	}

	private boolean isReusable(Entry<V> entry, long now) {
		if (now - entry.lastAccessNanos > expireAfterAccessNanos)
			return false;

		CompletableFuture<V> future = entry.future;
		if (!future.isDone())
			return true; // in-flight load: reuse to coalesce

		return !future.isCompletedExceptionally() && future.getNow(null) != null;
	}

	private @Nullable V presentValue(@Nullable Entry<V> entry, long now) {
		if (entry == null || now - entry.lastAccessNanos > expireAfterAccessNanos)
			return null;

		CompletableFuture<V> future = entry.future;
		if (!future.isDone() || future.isCompletedExceptionally())
			return null;

		return future.getNow(null);
	}

	private void evictIfNeeded() {
		while (store.size() > maximumSize) {
			Map.@Nullable Entry<K, Entry<V>> oldest = null;
			for (Map.Entry<K, Entry<V>> e : store.entrySet()) {
				if (oldest == null || e.getValue().lastAccessNanos < oldest.getValue().lastAccessNanos)
					oldest = e;
			}
			if (oldest == null)
				break;
			store.remove(oldest.getKey(), oldest.getValue());
		}
	}

	/** The synchronous view, mirroring the subset of Caffeine's {@code synchronous()} API in use. */
	final class Sync {
		private final MapView mapView = new MapView();

		MapView asMap() {
			return mapView;
		}

		@Nullable V getIfPresent(K key) {
			long now = System.nanoTime();
			Entry<V> entry = store.get(key);
			V value = presentValue(entry, now);
			if (value != null && entry != null)
				entry.lastAccessNanos = now;
			return value;
		}

		void invalidate(K key) {
			store.remove(key);
		}

		void invalidateAll(Iterable<? extends K> keys) {
			for (K key : keys)
				store.remove(key);
		}

		void invalidateAll() {
			store.clear();
		}
	}

	/** The minimal map view used by the client: only {@code compute} for value insertion/replacement. */
	final class MapView {
		@Nullable V compute(K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> remappingFunction) {
			long now = System.nanoTime();
			Entry<V> entry = store.compute(key, (k, current) -> {
				V currentValue = presentValue(current, now);
				V newValue = remappingFunction.apply(k, currentValue);
				if (newValue == null)
					return null;
				return new Entry<>(CompletableFuture.completedFuture(newValue), now);
			});
			evictIfNeeded();
			return entry == null ? null : entry.future.getNow(null);
		}
	}

	private static final class Entry<V> {
		final CompletableFuture<V> future;
		volatile long lastAccessNanos;

		Entry(CompletableFuture<V> future, long lastAccessNanos) {
			this.future = future;
			this.lastAccessNanos = lastAccessNanos;
		}
	}
}
