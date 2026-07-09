package com.example.ratelimiter.storage;

import com.example.ratelimiter.model.RateLimitCounters;

/**
 * Storage abstraction for rate-limit counters.
 *
 * This is the single seam between business logic ({@code service} package)
 * and persistence. {@link com.example.ratelimiter.service.RateLimiterServiceImpl}
 * only ever talks to this interface, so swapping {@link InMemoryRateLimitStore}
 * for {@link RedisRateLimitStore} (or any future backend) requires zero
 * changes above this layer - just a different {@code @ConditionalOnProperty}
 * bean selected via {@code ratelimiter.storage.type}.
 *
 * Implementations MUST perform the increment atomically: read-current,
 * roll-over-if-expired, add-and-write must happen as one indivisible step
 * per key, since multiple threads/requests hit the same client key
 * concurrently.
 */
public interface RateLimitStore {

    /**
     * Atomically increments the request and token counters for {@code key}
     * within its current fixed window, creating a fresh window (with a new
     * TTL of {@code windowSeconds}) if none exists yet or the previous one
     * has expired.
     *
     * @param key            fully-qualified counter key, e.g. {@code "rpm:clientA"}
     * @param windowSeconds  fixed window length in seconds
     * @param requestCost    how much to add to the request counter (normally 1)
     * @param tokenCost      how much to add to the token counter
     * @return the counters and reset time AFTER applying this increment
     */
    RateLimitCounters incrementAndGet(String key, long windowSeconds, long requestCost, long tokenCost);

    /**
     * Reads the current counters for {@code key} without mutating them.
     * Returns a zeroed-out window with no active TTL if the key does not
     * exist (i.e. the client hasn't made a request in the current window).
     */
    RateLimitCounters peek(String key, long windowSeconds);
}
