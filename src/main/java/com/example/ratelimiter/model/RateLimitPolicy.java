package com.example.ratelimiter.model;

/**
 * The effective RPM/TPM ceilings and window length that apply to one client,
 * after resolving any per-client override against the global default.
 */
public record RateLimitPolicy(long requestsPerMinute, long tokensPerMinute, long windowSeconds) {
}
