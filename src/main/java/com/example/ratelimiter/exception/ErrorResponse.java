package com.example.ratelimiter.exception;

import java.time.Instant;
import java.util.List;

/** Uniform error payload returned by {@link GlobalExceptionHandler}. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, List.of());
    }

    public static ErrorResponse of(int status, String error, String message, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, details);
    }
}
