package com.example.ratelimiter.service;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.model.RateLimitPolicy;
import org.springframework.stereotype.Component;

/**
 * Resolves which {@link RateLimitPolicy} applies to a given client: an
 * explicit per-client override from {@code ratelimiter.client-policies.*} if
 * one is configured, otherwise the global {@code ratelimiter.default-policy}.
 */
@Component
public class PolicyResolver {

    private final RateLimiterProperties properties;

    public PolicyResolver(RateLimiterProperties properties) {
        this.properties = properties;
    }

    public RateLimitPolicy resolve(String clientId) {
        RateLimiterProperties.Policy configured = properties.getClientPolicies().get(clientId);
        if (configured == null) {
            configured = properties.getDefaultPolicy();
        }
        return new RateLimitPolicy(
                configured.getRequestsPerMinute(),
                configured.getTokensPerMinute(),
                properties.getWindowSeconds());
    }
}
