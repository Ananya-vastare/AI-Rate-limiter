package com.example.ratelimiter.model;

/**
 * Raw counters for a single client's current fixed window, as returned by
 * the {@link com.example.ratelimiter.storage.RateLimitStore}. This type is
 * storage-agnostic: both the in-memory and Redis implementations produce it,
 * which is what lets {@code RateLimiterServiceImpl} stay unaware of which
 * backend is in play.
 *
 * @param requestCount        total requests recorded so far in this window
 * @param tokenCount          total tokens recorded so far in this window
 * @param windowResetEpochSec epoch-second timestamp when this window expires
 */
public record RateLimitCounters(long requestCount, long tokenCount, long windowResetEpochSec) {
}
