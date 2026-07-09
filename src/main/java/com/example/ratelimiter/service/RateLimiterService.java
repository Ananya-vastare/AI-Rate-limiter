package com.example.ratelimiter.service;

import com.example.ratelimiter.model.RateLimitCheckResponse;

public interface RateLimiterService {

    /**
     * Consumes one request and {@code estimatedTokens} tokens from the
     * client's current window and reports whether the call is allowed.
     */
    RateLimitCheckResponse check(String clientId, long estimatedTokens);

    /** Read-only view of a client's current quota, without consuming any. */
    RateLimitCheckResponse status(String clientId);
}
