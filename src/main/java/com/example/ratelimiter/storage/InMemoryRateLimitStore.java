package com.example.ratelimiter.storage;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitCounters;
import com.example.ratelimiter.util.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default, dependency-free {@link RateLimitStore} backed by a
 * {@link ConcurrentHashMap}. Active whenever {@code ratelimiter.storage.type}
 * is {@code memory} (the default) or unset.
 *
 * <h2>Concurrency</h2>
 * Each key's window is an immutable {@link WindowEntry} snapshot. Updates go
 * through {@link ConcurrentHashMap#compute}, which Java guarantees is applied
 * atomically per key (internally synchronized on that key's bin), so
 * concurrent requests for the same client can never lose an increment or
 * read a torn/half-updated state - the same "read-modify-write in one hop"
 * guarantee a Redis Lua script gives you server-side.
 *
 * <h2>Expiry</h2>
 * There is no native TTL for a plain map entry, so a window is treated as
 * expired the moment {@code now >= windowStart + windowSeconds}, at which
 * point the next increment silently starts a fresh window. A background
 * {@link #evictExpiredWindows()} sweep additionally removes stale entries so
 * idle clients don't leak memory - mirroring how Redis would passively
 * expire the key itself via TTL.
 */
@Component
@ConditionalOnProperty(prefix = "ratelimiter.storage", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimitStore.class);

    private final Map<String, WindowEntry> windows = new ConcurrentHashMap<>();
    private final TimeProvider timeProvider;
    private final RateLimiterProperties properties;

    public InMemoryRateLimitStore(TimeProvider timeProvider, RateLimiterProperties properties) {
        this.timeProvider = timeProvider;
        this.properties = properties;
    }

    @Override
    public RateLimitCounters incrementAndGet(String key, long windowSeconds, long requestCost, long tokenCost) {
        long now = timeProvider.currentEpochSecond();

        WindowEntry updated = windows.compute(key, (k, existing) -> {
            if (existing == null || isExpired(existing, windowSeconds, now)) {
                return new WindowEntry(now, requestCost, tokenCost);
            }
            return new WindowEntry(existing.windowStartEpoch, existing.requestCount + requestCost,
                    existing.tokenCount + tokenCost);
        });

        return toCounters(updated, windowSeconds);
    }

    @Override
    public RateLimitCounters peek(String key, long windowSeconds) {
        long now = timeProvider.currentEpochSecond();
        WindowEntry entry = windows.get(key);

        if (entry == null || isExpired(entry, windowSeconds, now)) {
            return new RateLimitCounters(0, 0, now + windowSeconds);
        }
        return toCounters(entry, windowSeconds);
    }

    private boolean isExpired(WindowEntry entry, long windowSeconds, long now) {
        return now >= entry.windowStartEpoch + windowSeconds;
    }

    private RateLimitCounters toCounters(WindowEntry entry, long windowSeconds) {
        return new RateLimitCounters(entry.requestCount, entry.tokenCount, entry.windowStartEpoch + windowSeconds);
    }

    /**
     * TTL-style janitor: periodically drops windows that expired and were
     * never touched again, freeing memory for clients that went idle -
     * exactly what Redis would do for you automatically via key TTL.
     */
    @Scheduled(fixedDelayString = "${ratelimiter.cleanup.interval-ms:30000}")
    void evictExpiredWindows() {
        if (!properties.getCleanup().isEnabled()) {
            return;
        }
        long now = timeProvider.currentEpochSecond();
        long windowSeconds = properties.getWindowSeconds();
        int before = windows.size();

        windows.entrySet().removeIf(e -> isExpired(e.getValue(), windowSeconds, now));

        int removed = before - windows.size();
        if (removed > 0) {
            log.debug("Evicted {} expired rate-limit window(s); {} active", removed, windows.size());
        }
    }

    /** Package-visible for tests that want to assert eviction behaviour. */
    int size() {
        return windows.size();
    }

    private record WindowEntry(long windowStartEpoch, long requestCount, long tokenCount) {
    }
}
