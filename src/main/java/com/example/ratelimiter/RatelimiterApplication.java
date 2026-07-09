package com.example.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI-Aware Rate Limiter.
 *
 * Enforces both Requests-Per-Minute (RPM) and Tokens-Per-Minute (TPM) limits
 * per client, backed by a pluggable {@link com.example.ratelimiter.storage.RateLimitStore}.
 *
 * {@code @EnableScheduling} powers the in-memory store's TTL-style cleanup job,
 * which mimics how Redis would passively expire keys.
 */
@SpringBootApplication
@EnableScheduling
public class RatelimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RatelimiterApplication.class, args);
    }
}
