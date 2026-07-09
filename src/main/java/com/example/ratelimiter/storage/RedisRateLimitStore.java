package com.example.ratelimiter.storage;

import com.example.ratelimiter.model.RateLimitCounters;
import com.example.ratelimiter.util.TimeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Redis-backed {@link RateLimitStore}, activated with
 * {@code ratelimiter.storage.type=redis}. This is the "production" backend:
 * counters live in a Redis hash per client key with a native TTL, and the
 * increment is performed by {@code scripts/rate_limit.lua} so the
 * check-and-increment stays atomic even across multiple app instances -
 * something a plain {@code ConcurrentHashMap} can only guarantee within a
 * single JVM.
 *
 * Notice this class implements the exact same {@link RateLimitStore}
 * contract as {@link InMemoryRateLimitStore}. Nothing in
 * {@code service} or {@code controller} needed to change to support this;
 * only this file and {@link com.example.ratelimiter.config.RedisConfig}
 * were added.
 */
@Component
@ConditionalOnProperty(prefix = "ratelimiter.storage", name = "type", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimitScript;
    private final TimeProvider timeProvider;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate,
                                RedisScript<List> rateLimitScript,
                                TimeProvider timeProvider) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.timeProvider = timeProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitCounters incrementAndGet(String key, long windowSeconds, long requestCost, long tokenCost) {
        List<Long> result = redisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(windowSeconds),
                String.valueOf(requestCost),
                String.valueOf(tokenCost));

        long requestCount = result.get(0);
        long tokenCount = result.get(1);
        long ttlSeconds = result.get(2);

        return new RateLimitCounters(requestCount, tokenCount, timeProvider.currentEpochSecond() + ttlSeconds);
    }

    @Override
    public RateLimitCounters peek(String key, long windowSeconds) {
        Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
        Long ttl = redisTemplate.getExpire(key);
        long now = timeProvider.currentEpochSecond();

        if (hash.isEmpty() || ttl == null || ttl < 0) {
            return new RateLimitCounters(0, 0, now + windowSeconds);
        }

        long requestCount = Long.parseLong(String.valueOf(hash.getOrDefault("requests", "0")));
        long tokenCount = Long.parseLong(String.valueOf(hash.getOrDefault("tokens", "0")));

        return new RateLimitCounters(requestCount, tokenCount, now + ttl);
    }
}
