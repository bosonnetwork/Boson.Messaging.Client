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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Future;
import io.vertx.core.Promise;

import org.junit.jupiter.api.Test;

class AsyncCacheTests {
	private record Removal(String key, String value, AsyncCache.RemovalCause cause) {
	}

	@Test
	void coalescesConcurrentLoadsForSameKey() {
		AtomicInteger loads = new AtomicInteger();
		Promise<String> promise = Promise.promise();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.build(key -> {
					loads.incrementAndGet();
					return promise.future();
				});

		Future<String> first = cache.get("a");
		Future<String> second = cache.get("a");
		promise.complete("value");

		assertSame(first, second);
		assertEquals(1, loads.get());
		assertEquals("value", first.toCompletionStage().toCompletableFuture().join());
	}

	@Test
	void expiresAfterWriteAndReloads() throws InterruptedException {
		AtomicInteger loads = new AtomicInteger();
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.expireAfterWrite(Duration.ofMillis(10))
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture(key + loads.incrementAndGet()));

		assertEquals("a1", cache.get("a").toCompletionStage().toCompletableFuture().join());
		Thread.sleep(25);
		assertEquals("a2", cache.get("a").toCompletionStage().toCompletableFuture().join());

		assertEquals(List.of(new Removal("a", "a1", AsyncCache.RemovalCause.EXPIRED)), removals);
	}

	@Test
	void expiresAfterAccessOnlyAfterIdleTime() throws InterruptedException {
		AtomicInteger loads = new AtomicInteger();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.expireAfterAccess(Duration.ofMillis(30))
				.build(key -> Future.succeededFuture(key + loads.incrementAndGet()));

		assertEquals("a1", cache.get("a").toCompletionStage().toCompletableFuture().join());
		Thread.sleep(15);
		assertEquals("a1", cache.synchronous().getIfPresent("a"));
		Thread.sleep(20);
		assertEquals("a1", cache.synchronous().getIfPresent("a"));
		Thread.sleep(35);
		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals("a2", cache.get("a").toCompletionStage().toCompletableFuture().join());
	}

	@Test
	void evictsLeastRecentlyAccessedEntryWhenMaximumSizeExceeded() {
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.initialCapacity(4)
				.maximumSize(2)
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture(key.toUpperCase()));

		cache.get("a").toCompletionStage().toCompletableFuture().join();
		cache.get("b").toCompletionStage().toCompletableFuture().join();
		assertEquals("A", cache.synchronous().getIfPresent("a"));
		cache.get("c").toCompletionStage().toCompletableFuture().join();

		assertEquals("A", cache.synchronous().getIfPresent("a"));
		assertNull(cache.synchronous().getIfPresent("b"));
		assertEquals("C", cache.synchronous().getIfPresent("c"));
		assertEquals(List.of(new Removal("b", "B", AsyncCache.RemovalCause.SIZE)), removals);
	}

	@Test
	void reportsReplacementAndExplicitInvalidationToRemovalListener() {
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture("loaded"));

		cache.get("a").toCompletionStage().toCompletableFuture().join();
		cache.synchronous().asMap().compute("a", (key, current) -> "manual");
		cache.synchronous().invalidate("a");

		assertEquals(List.of(
				new Removal("a", "loaded", AsyncCache.RemovalCause.REPLACED),
				new Removal("a", "manual", AsyncCache.RemovalCause.EXPLICIT)), removals);
	}

	@Test
	void failedLoadsAreNotRetained() {
		AtomicInteger loads = new AtomicInteger();
		RuntimeException failure = new RuntimeException("boom");
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.build(key -> {
					int attempt = loads.incrementAndGet();
					return attempt == 1 ? Future.failedFuture(failure) : Future.succeededFuture("value" + attempt);
				});

		Future<String> failed = cache.get("a");

		assertTrue(failed.failed());
		assertEquals(failure, failed.cause());
		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals("value2", cache.get("a").result());
		assertEquals(2, loads.get());
	}

	@Test
	void nullLoadsAreNotRetained() {
		AtomicInteger loads = new AtomicInteger();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.build(key -> Future.succeededFuture(loads.incrementAndGet() == 1 ? null : "value"));

		Future<String> missing = cache.get("a");

		assertTrue(missing.succeeded());
		assertNull(missing.result());
		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals("value", cache.get("a").result());
		assertEquals(2, loads.get());
	}

	@Test
	void completingInvalidatedInFlightLoadDoesNotRemoveNewerEntry() {
		List<Removal> removals = new ArrayList<>();
		Promise<String> firstLoad = Promise.promise();
		AtomicInteger loads = new AtomicInteger();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> loads.incrementAndGet() == 1 ? firstLoad.future() : Future.succeededFuture("new"));

		Future<String> stale = cache.get("a");
		cache.synchronous().invalidate("a");
		assertEquals("new", cache.get("a").result());

		firstLoad.complete("old");

		assertEquals("old", stale.result());
		assertEquals("new", cache.synchronous().getIfPresent("a"));
		assertEquals(List.of(new Removal("a", null, AsyncCache.RemovalCause.EXPLICIT)), removals);
	}

	@Test
	void cleanUpExpiresEntriesWithoutAccess() throws InterruptedException {
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.expireAfterWrite(Duration.ofMillis(10))
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture(key.toUpperCase()));

		cache.get("a");
		cache.get("b");
		Thread.sleep(25);
		cache.cleanUp();

		assertNull(cache.synchronous().getIfPresent("a"));
		assertNull(cache.synchronous().getIfPresent("b"));
		assertEquals(2, removals.size());
		assertTrue(removals.contains(new Removal("a", "A", AsyncCache.RemovalCause.EXPIRED)));
		assertTrue(removals.contains(new Removal("b", "B", AsyncCache.RemovalCause.EXPIRED)));
	}

	@Test
	void invalidateAllWithKeysAndAllNotifyListener() {
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture(key.toUpperCase()));

		cache.get("a");
		cache.get("b");
		cache.get("c");
		cache.synchronous().invalidateAll(List.of("a", "missing"));
		cache.synchronous().invalidateAll();

		assertNull(cache.synchronous().getIfPresent("a"));
		assertNull(cache.synchronous().getIfPresent("b"));
		assertNull(cache.synchronous().getIfPresent("c"));
		assertEquals(3, removals.size());
		assertTrue(removals.contains(new Removal("a", "A", AsyncCache.RemovalCause.EXPLICIT)));
		assertTrue(removals.contains(new Removal("b", "B", AsyncCache.RemovalCause.EXPLICIT)));
		assertTrue(removals.contains(new Removal("c", "C", AsyncCache.RemovalCause.EXPLICIT)));
	}

	@Test
	void computeCanRemoveExistingEntry() {
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture("loaded"));

		cache.get("a");
		String result = cache.synchronous().asMap().compute("a", (key, current) -> null);

		assertNull(result);
		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals(List.of(new Removal("a", "loaded", AsyncCache.RemovalCause.EXPLICIT)), removals);
	}

	@Test
	void maximumSizeZeroDoesNotRetainEntries() {
		List<Removal> removals = new ArrayList<>();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.maximumSize(0)
				.removalListener((key, value, cause) -> removals.add(new Removal(key, value, cause)))
				.build(key -> Future.succeededFuture("value"));

		assertEquals("value", cache.get("a").result());

		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals(List.of(new Removal("a", "value", AsyncCache.RemovalCause.SIZE)), removals);
	}

	@Test
	void builderRejectsInvalidArguments() {
		AsyncCache.Builder<String, String> builder = AsyncCache.newBuilder();

		assertThrows(IllegalArgumentException.class, () -> builder.initialCapacity(-1));
		assertThrows(IllegalArgumentException.class, () -> builder.maximumSize(-1));
		assertThrows(IllegalArgumentException.class, () -> builder.expireAfterAccess(Duration.ofNanos(-1)));
		assertThrows(IllegalArgumentException.class, () -> builder.expireAfterWrite(Duration.ofNanos(-1)));
		assertThrows(NullPointerException.class, () -> builder.expireAfterAccess(null));
		assertThrows(NullPointerException.class, () -> builder.expireAfterWrite(null));
		assertThrows(NullPointerException.class, () -> builder.removalListener(null));
		assertThrows(NullPointerException.class, () -> builder.build(null));
	}

	@Test
	void expireAfterWriteIsNotRefreshedByAccess() throws InterruptedException {
		AtomicInteger loads = new AtomicInteger();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.expireAfterWrite(Duration.ofMillis(50))
				.build(key -> Future.succeededFuture(key + loads.incrementAndGet()));

		assertEquals("a1", cache.get("a").result());
		// Access well within the write TTL; this refreshes access time but must not extend write.
		Thread.sleep(30);
		assertEquals("a1", cache.synchronous().getIfPresent("a"));

		// Total elapsed since write now exceeds the write TTL despite the recent access, so it
		// expires. Were write refreshed by access, the entry would still be present here.
		Thread.sleep(35);
		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals("a2", cache.get("a").result());
		assertEquals(2, loads.get());
	}

	@Test
	void expireAfterAccessAndWriteCombinedHonoursWriteDeadline() throws InterruptedException {
		AtomicInteger loads = new AtomicInteger();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.expireAfterAccess(Duration.ofMillis(50))
				.expireAfterWrite(Duration.ofMillis(80))
				.build(key -> Future.succeededFuture(key + loads.incrementAndGet()));

		assertEquals("a1", cache.get("a").result());
		// Repeated access (each gap < access TTL) keeps the entry alive past the access TTL...
		Thread.sleep(30);
		assertEquals("a1", cache.synchronous().getIfPresent("a"));
		Thread.sleep(30);
		assertEquals("a1", cache.synchronous().getIfPresent("a"));
		// ...but only until the write deadline: the last access (30ms ago) is within the access
		// TTL, yet the entry expires because > 80ms has elapsed since the write.
		Thread.sleep(30);
		assertNull(cache.synchronous().getIfPresent("a"));
		assertEquals("a2", cache.get("a").result());
		assertEquals(2, loads.get());
	}

	@Test
	void removalListenerExceptionIsIsolatedAndDoesNotBreakCache() {
		List<Removal> removals = new ArrayList<>();
		AtomicInteger calls = new AtomicInteger();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.removalListener((key, value, cause) -> {
					if (calls.incrementAndGet() == 1)
						throw new RuntimeException("listener boom");
					removals.add(new Removal(key, value, cause));
				})
				.build(key -> Future.succeededFuture(key.toUpperCase()));

		cache.get("a");
		cache.get("b");

		// First removal's listener throws but must not propagate out of invalidate().
		cache.synchronous().invalidate("a");
		// Cache stays usable and the next removal is still delivered.
		cache.synchronous().invalidate("b");

		assertNull(cache.synchronous().getIfPresent("a"));
		assertNull(cache.synchronous().getIfPresent("b"));
		assertEquals(2, calls.get());
		assertEquals(List.of(new Removal("b", "B", AsyncCache.RemovalCause.EXPLICIT)), removals);
	}

	@Test
	void rejectsNullArguments() {
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.build(key -> Future.succeededFuture(key.toUpperCase()));

		assertThrows(NullPointerException.class, () -> cache.get(null));
		assertThrows(NullPointerException.class, () -> cache.synchronous().getIfPresent(null));
		assertThrows(NullPointerException.class, () -> cache.synchronous().invalidate(null));
		assertThrows(NullPointerException.class, () -> cache.synchronous().invalidateAll(null));
		assertThrows(NullPointerException.class, () -> cache.synchronous().asMap().compute(null, (k, v) -> "x"));
		assertThrows(NullPointerException.class, () -> cache.synchronous().asMap().compute("a", null));
	}

	@Test
	void getIfPresentReturnsNullForAbsentKey() {
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.build(key -> Future.succeededFuture(key.toUpperCase()));

		assertNull(cache.synchronous().getIfPresent("missing"));
	}

	@Test
	void synchronousLookupReturnsNullForInFlightEntry() {
		Promise<String> promise = Promise.promise();
		AsyncCache<String, String> cache = AsyncCache.<String, String>newBuilder()
				.build(key -> promise.future());

		Future<String> loading = cache.get("a");

		assertFalse(loading.isComplete());
		assertNull(cache.synchronous().getIfPresent("a"));

		promise.complete("value");
		assertEquals("value", cache.synchronous().getIfPresent("a"));
	}
}
