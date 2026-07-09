package com.example.ratelimiter.util;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Thin seam around {@link Instant#now()} so window-rollover logic can be
 * unit-tested with a fake clock instead of sleeping real threads.
 */
public interface TimeProvider {
    long currentEpochSecond();

    @Component
    class SystemTimeProvider implements TimeProvider {
        @Override
        public long currentEpochSecond() {
            return Instant.now().getEpochSecond();
        }
    }
}
