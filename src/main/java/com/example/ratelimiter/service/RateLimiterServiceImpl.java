package com.example.ratelimiter.service;

import com.example.ratelimiter.exception.StoreUnavailableException;
import com.example.ratelimiter.model.RateLimitCheckResponse;
import com.example.ratelimiter.model.RateLimitCounters;
import com.example.ratelimiter.model.RateLimitPolicy;
import com.example.ratelimiter.storage.RateLimitStore;
import com.example.ratelimiter.util.TimeProvider;
import org.springframework.stereotype.Service;

/**
 * Applies RPM + TPM policy limits on top of raw counters supplied by
 * whichever {@link RateLimitStore} is currently wired in.
 *
 * This class is intentionally storage-agnostic: it never imports
 * {@code InMemoryRateLimitStore} or {@code RedisRateLimitStore} directly, so
 * the business rule ("allowed = both counters are within their limits after
 * incrementing") holds identically no matter which backend answers
 * {@link RateLimitStore#incrementAndGet}.
 */
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:";

    private final RateLimitStore store;
    private final PolicyResolver policyResolver;
    private final TimeProvider timeProvider;

    public RateLimiterServiceImpl(RateLimitStore store, PolicyResolver policyResolver, TimeProvider timeProvider) {
        this.store = store;
        this.policyResolver = policyResolver;
        this.timeProvider = timeProvider;
    }

    @Override
    public RateLimitCheckResponse check(String clientId, long estimatedTokens) {
        RateLimitPolicy policy = policyResolver.resolve(clientId);
        String key = KEY_PREFIX + clientId;

        RateLimitCounters counters;
        try {
            counters = store.incrementAndGet(key, policy.windowSeconds(), 1, estimatedTokens);
        } catch (RuntimeException ex) {
            throw new StoreUnavailableException("Failed to update rate limit counters for client '" + clientId + "'", ex);
        }

        return toResponse(clientId, policy, counters);
    }

    @Override
    public RateLimitCheckResponse status(String clientId) {
        RateLimitPolicy policy = policyResolver.resolve(clientId);
        String key = KEY_PREFIX + clientId;

        RateLimitCounters counters;
        try {
            counters = store.peek(key, policy.windowSeconds());
        } catch (RuntimeException ex) {
            throw new StoreUnavailableException("Failed to read rate limit counters for client '" + clientId + "'", ex);
        }

        return toResponse(clientId, policy, counters);
    }

    private RateLimitCheckResponse toResponse(String clientId, RateLimitPolicy policy, RateLimitCounters counters) {
        long remainingRequests = Math.max(0, policy.requestsPerMinute() - counters.requestCount());
        long remainingTokens = Math.max(0, policy.tokensPerMinute() - counters.tokenCount());

        boolean withinRequestLimit = counters.requestCount() <= policy.requestsPerMinute();
        boolean withinTokenLimit = counters.tokenCount() <= policy.tokensPerMinute();
        boolean allowed = withinRequestLimit && withinTokenLimit;

        String reason = null;
        if (!withinRequestLimit && !withinTokenLimit) {
            reason = "RPM_AND_TPM_EXCEEDED";
        } else if (!withinRequestLimit) {
            reason = "RPM_EXCEEDED";
        } else if (!withinTokenLimit) {
            reason = "TPM_EXCEEDED";
        }

        long now = timeProvider.currentEpochSecond();
        long resetInSeconds = Math.max(0, counters.windowResetEpochSec() - now);

        return new RateLimitCheckResponse(
                allowed,
                clientId,
                policy.requestsPerMinute(),
                remainingRequests,
                policy.tokensPerMinute(),
                remainingTokens,
                resetInSeconds,
                counters.windowResetEpochSec(),
                reason);
    }
}
