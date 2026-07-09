package com.example.ratelimiter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Wiring for the Redis-backed store. This whole class is skipped by Spring
 * (and no connection to Redis is attempted) unless the application is
 * explicitly configured with {@code ratelimiter.storage.type=redis}.
 */
@Configuration
@ConditionalOnProperty(prefix = "ratelimiter.storage", name = "type", havingValue = "redis")
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Atomic fixed-window increment, mirroring the semantics of
     * {@link com.example.ratelimiter.storage.InMemoryRateLimitStore}:
     * bump request/token counters and set a TTL only when the window key is
     * first created. Running this as a single Lua script guarantees
     * atomicity across the read-check-increment-expire sequence, which is
     * exactly the race condition ConcurrentHashMap#compute avoids in-memory.
     */
    @Bean
    public RedisScript<List> rateLimitScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/rate_limit.lua"));
        script.setResultType(List.class);
        return script;
    }
}
