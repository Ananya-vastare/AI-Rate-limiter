package com.example.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the full application context starts with the default
 * configuration (in-memory storage) and therefore requires no Redis
 * instance to be running.
 */
@SpringBootTest
class RatelimiterApplicationTests {

    @Test
    void contextLoads() {
    }
}
