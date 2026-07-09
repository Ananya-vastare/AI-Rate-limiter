package com.example.ratelimiter.exception;

/** Base type for all rate-limiter domain errors. */
public class RateLimiterException extends RuntimeException {
    public RateLimiterException(String message) {
        super(message);
    }

    public RateLimiterException(String message, Throwable cause) {
        super(message, cause);
    }
}
