package com.example.ratelimiter.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload for {@code POST /api/v1/rate-limit/check}.
 *
 * @param clientId        caller identity (API key, user id, tenant id, ...)
 * @param estimatedTokens number of tokens the caller expects this AI request
 *                        to consume (e.g. prompt + max completion tokens).
 *                        Defaults to 0 when omitted, so pure RPM-only
 *                        callers can leave it out entirely.
 */
public record RateLimitCheckRequest(

        @NotBlank(message = "clientId must not be blank")
        String clientId,

        @Min(value = 0, message = "estimatedTokens must not be negative")
        Long estimatedTokens
) {
    public long tokensOrZero() {
        return estimatedTokens == null ? 0L : estimatedTokens;
    }
}
