package com.example.ratelimiter.exception;

/** Thrown when the underlying counter store cannot be reached or read. */
public class StoreUnavailableException extends RateLimiterException {
    public StoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
