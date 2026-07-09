package com.example.ratelimiter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Externalized configuration for the rate limiter, bound from
 * {@code application.properties} under the {@code ratelimiter} prefix.
 *
 * <pre>
 * ratelimiter.storage.type=memory            # memory | redis
 * ratelimiter.window-seconds=60
 * ratelimiter.default-policy.requests-per-minute=60
 * ratelimiter.default-policy.tokens-per-minute=10000
 * ratelimiter.client-policies.premium-client.requests-per-minute=600
 * ratelimiter.client-policies.premium-client.tokens-per-minute=100000
 * ratelimiter.cleanup.interval-ms=30000
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {

    /** Storage backend selector: "memory" (default) or "redis". */
    @NotBlank
    private String storageType = "memory";

    /** Fixed window size, in seconds, used for both RPM and TPM buckets. */
    @Min(1)
    private long windowSeconds = 60;

    @NestedConfigurationProperty
    private Policy defaultPolicy = new Policy(60, 10_000);

    /** Optional per-client overrides, keyed by clientId. */
    private Map<String, Policy> clientPolicies = new HashMap<>();

    @NestedConfigurationProperty
    private Cleanup cleanup = new Cleanup();

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Policy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(Policy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public Map<String, Policy> getClientPolicies() {
        return clientPolicies;
    }

    public void setClientPolicies(Map<String, Policy> clientPolicies) {
        this.clientPolicies = clientPolicies;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public void setCleanup(Cleanup cleanup) {
        this.cleanup = cleanup;
    }

    /** A single client's RPM + TPM limits. */
    public static class Policy {
        @Min(1)
        private long requestsPerMinute;

        @Min(1)
        private long tokensPerMinute;

        public Policy() {
        }

        public Policy(long requestsPerMinute, long tokensPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
            this.tokensPerMinute = tokensPerMinute;
        }

        public long getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public void setRequestsPerMinute(long requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }

        public long getTokensPerMinute() {
            return tokensPerMinute;
        }

        public void setTokensPerMinute(long tokensPerMinute) {
            this.tokensPerMinute = tokensPerMinute;
        }
    }

    /** Housekeeping for the in-memory store's expired-window sweeper. */
    public static class Cleanup {
        private boolean enabled = true;

        @Min(1000)
        private long intervalMs = 30_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }
}
