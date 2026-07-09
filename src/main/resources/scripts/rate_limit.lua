-- Atomic fixed-window increment for combined RPM + TPM counters.
--
-- KEYS[1] = counter key, e.g. "ratelimit:clientA"
-- ARGV[1] = window length in seconds
-- ARGV[2] = request cost to add (normally 1)
-- ARGV[3] = token cost to add
--
-- Returns: { requestCount, tokenCount, ttlSecondsRemaining }
--
-- Running the whole read-check-increment-expire sequence as a single script
-- makes it atomic from Redis's point of view - equivalent in spirit to the
-- per-key lock ConcurrentHashMap#compute gives the in-memory store.

local key = KEYS[1]
local window_seconds = tonumber(ARGV[1])
local request_cost = tonumber(ARGV[2])
local token_cost = tonumber(ARGV[3])

local request_count
local token_count

if redis.call('EXISTS', key) == 1 then
    request_count = redis.call('HINCRBY', key, 'requests', request_cost)
    token_count = redis.call('HINCRBY', key, 'tokens', token_cost)
else
    redis.call('HSET', key, 'requests', request_cost, 'tokens', token_cost)
    redis.call('EXPIRE', key, window_seconds)
    request_count = request_cost
    token_count = token_cost
end

local ttl = redis.call('TTL', key)
if ttl < 0 then
    -- Defensive: key somehow has no TTL (e.g. pre-existing key without one).
    redis.call('EXPIRE', key, window_seconds)
    ttl = window_seconds
end

return { request_count, token_count, ttl }
