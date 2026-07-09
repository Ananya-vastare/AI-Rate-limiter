package com.example.ratelimiter.storage;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitCounters;
import com.example.ratelimiter.util.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    /** Deterministic, manually-advanced clock so window rollover is testable without sleeping. */
    private static class FakeTimeProvider implements TimeProvider {
        private final AtomicLong nowSeconds;

        FakeTimeProvider(long start) {
            this.nowSeconds = new AtomicLong(start);
        }

        @Override
        public long currentEpochSecond() {
            return nowSeconds.get();
        }

        void advance(long seconds) {
            nowSeconds.addAndGet(seconds);
        }
    }

    private FakeTimeProvider clock;
    private InMemoryRateLimitStore store;

    @BeforeEach
    void setUp() {
        clock = new FakeTimeProvider(1_000_000L);
        RateLimiterProperties props = new RateLimiterProperties();
        store = new InMemoryRateLimitStore(clock, props);
    }

    @Test
    void firstIncrementCreatesFreshWindow() {
        RateLimitCounters counters = store.incrementAndGet("clientA", 60, 1, 100);

        assertThat(counters.requestCount()).isEqualTo(1);
        assertThat(counters.tokenCount()).isEqualTo(100);
        assertThat(counters.windowResetEpochSec()).isEqualTo(1_000_000L + 60);
    }

    @Test
    void subsequentIncrementsAccumulateWithinSameWindow() {
        store.incrementAndGet("clientA", 60, 1, 100);
        store.incrementAndGet("clientA", 60, 1, 50);
        RateLimitCounters counters = store.incrementAndGet("clientA", 60, 1, 25);

        assertThat(counters.requestCount()).isEqualTo(3);
        assertThat(counters.tokenCount()).isEqualTo(175);
    }

    @Test
    void windowRollsOverAfterExpiry() {
        store.incrementAndGet("clientA", 60, 1, 100);

        clock.advance(61); // past the 60s window

        RateLimitCounters counters = store.incrementAndGet("clientA", 60, 1, 10);

        assertThat(counters.requestCount()).isEqualTo(1); // reset, not 2
        assertThat(counters.tokenCount()).isEqualTo(10);
        assertThat(counters.windowResetEpochSec()).isEqualTo(clock.currentEpochSecond() + 60);
    }

    @Test
    void peekDoesNotMutateState() {
        store.incrementAndGet("clientA", 60, 1, 100);

        store.peek("clientA", 60);
        store.peek("clientA", 60);
        RateLimitCounters counters = store.peek("clientA", 60);

        assertThat(counters.requestCount()).isEqualTo(1);
        assertThat(counters.tokenCount()).isEqualTo(100);
    }

    @Test
    void peekOnUnknownKeyReturnsZeroedWindow() {
        RateLimitCounters counters = store.peek("neverSeen", 60);

        assertThat(counters.requestCount()).isZero();
        assertThat(counters.tokenCount()).isZero();
        assertThat(counters.windowResetEpochSec()).isEqualTo(clock.currentEpochSecond() + 60);
    }

    @Test
    void concurrentIncrementsForSameClientAreNotLost() throws InterruptedException {
        int threads = 50;
        int incrementsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        store.incrementAndGet("hotClient", 60, 1, 1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        RateLimitCounters counters = store.peek("hotClient", 60);
        assertThat(counters.requestCount()).isEqualTo((long) threads * incrementsPerThread);
        assertThat(counters.tokenCount()).isEqualTo((long) threads * incrementsPerThread);
    }

    @Test
    void independentClientsDoNotInterfere() {
        store.incrementAndGet("clientA", 60, 1, 100);
        store.incrementAndGet("clientB", 60, 1, 5);

        assertThat(store.peek("clientA", 60).requestCount()).isEqualTo(1);
        assertThat(store.peek("clientB", 60).requestCount()).isEqualTo(1);
        assertThat(store.peek("clientA", 60).tokenCount()).isEqualTo(100);
        assertThat(store.peek("clientB", 60).tokenCount()).isEqualTo(5);
    }
}
