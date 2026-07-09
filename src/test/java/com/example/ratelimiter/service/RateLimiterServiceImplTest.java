package com.example.ratelimiter.service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitCheckResponse;
import com.example.ratelimiter.model.RateLimitCounters;
import com.example.ratelimiter.storage.RateLimitStore;
import com.example.ratelimiter.util.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceImplTest {

    /** Minimal in-test double so this suite doesn't depend on either real storage backend. */
    private static class FakeStore implements RateLimitStore {
        private final Map<String, RateLimitCounters> data = new HashMap<>();

        @Override
        public RateLimitCounters incrementAndGet(String key, long windowSeconds, long requestCost, long tokenCost) {
            RateLimitCounters existing = data.get(key);
            long req = (existing == null ? 0 : existing.requestCount()) + requestCost;
            long tok = (existing == null ? 0 : existing.tokenCount()) + tokenCost;
            RateLimitCounters updated = new RateLimitCounters(req, tok, 1_000_060L);
            data.put(key, updated);
            return updated;
        }

        @Override
        public RateLimitCounters peek(String key, long windowSeconds) {
            return data.getOrDefault(key, new RateLimitCounters(0, 0, 1_000_060L));
        }
    }

    private static class FixedTimeProvider implements TimeProvider {
        @Override
        public long currentEpochSecond() {
            return 1_000_000L;
        }
    }

    private FakeStore store;
    private RateLimiterProperties properties;
    private RateLimiterServiceImpl service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        properties = new RateLimiterProperties();
        properties.setWindowSeconds(60);
        properties.getDefaultPolicy().setRequestsPerMinute(3);
        properties.getDefaultPolicy().setTokensPerMinute(1000);

        service = new RateLimiterServiceImpl(store, new PolicyResolver(properties), new FixedTimeProvider());
    }

    @Test
    void allowsRequestsWithinBothLimits() {
        RateLimitCheckResponse response = service.check("clientA", 100);

        assertThat(response.allowed()).isTrue();
        assertThat(response.remainingRequests()).isEqualTo(2);
        assertThat(response.remainingTokens()).isEqualTo(900);
        assertThat(response.reason()).isNull();
    }

    @Test
    void deniesOnceRequestLimitExceeded() {
        service.check("clientA", 10);
        service.check("clientA", 10);
        service.check("clientA", 10);
        RateLimitCheckResponse fourth = service.check("clientA", 10);

        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.reason()).isEqualTo("RPM_EXCEEDED");
        assertThat(fourth.remainingRequests()).isZero();
    }

    @Test
    void deniesOnceTokenLimitExceeded() {
        RateLimitCheckResponse response = service.check("clientB", 1500);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("TPM_EXCEEDED");
        assertThat(response.remainingTokens()).isZero();
    }

    @Test
    void perClientPolicyOverrideIsRespected() {
        RateLimiterProperties.Policy premium = new RateLimiterProperties.Policy(1000, 1_000_000);
        properties.getClientPolicies().put("vip", premium);

        RateLimitCheckResponse response = service.check("vip", 50_000);

        assertThat(response.allowed()).isTrue();
        assertThat(response.requestLimit()).isEqualTo(1000);
        assertThat(response.tokenLimit()).isEqualTo(1_000_000);
    }

    @Test
    void statusDoesNotConsumeQuota() {
        service.check("clientC", 100);

        RateLimitCheckResponse status1 = service.status("clientC");
        RateLimitCheckResponse status2 = service.status("clientC");

        assertThat(status1.remainingRequests()).isEqualTo(status2.remainingRequests());
        assertThat(status1.remainingRequests()).isEqualTo(2);
    }

    @Test
    void clientsAreIsolatedFromEachOther() {
        service.check("clientX", 10);
        service.check("clientX", 10);
        RateLimitCheckResponse other = service.check("clientY", 10);

        assertThat(other.remainingRequests()).isEqualTo(2); // unaffected by clientX's usage
    }
}
