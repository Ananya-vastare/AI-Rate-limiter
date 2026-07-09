# AI-Aware Rate Limiter

A production-style rate limiter for AI/LLM APIs that enforces **both**
Requests-Per-Minute (RPM) **and** Tokens-Per-Minute (TPM) per client, built
with Java 21 + Spring Boot 3 + Maven.

Runs entirely in-memory out of the box — **no Redis, no SQL database, no
external services required.** The storage layer is interface-based, so a
Redis-backed implementation is already included and can be switched on with
a single property.

## Why RPM *and* TPM?

Most rate limiters only count requests. That's insufficient for LLM APIs,
where a single request can cost anywhere from a few tokens to tens of
thousands. A client sending one huge request per minute can still blow
through your model provider's throughput budget even while staying well
under an RPM cap — TPM closes that gap.

## Architecture

```
controller/   REST endpoints (HTTP concerns only)
service/      Business rules: policy resolution, allow/deny decision
storage/      RateLimitStore interface + InMemory / Redis implementations
model/        DTOs and value types shared across layers
config/       @ConfigurationProperties, conditional bean wiring
exception/    Domain exceptions + @RestControllerAdvice
util/         TimeProvider seam for deterministic testing
```

The dependency direction is strictly one-way:

```
controller -> service -> storage (interface)
                            ^
                            |
              InMemoryRateLimitStore / RedisRateLimitStore
```

`RateLimiterServiceImpl` depends only on the `RateLimitStore` **interface**.
It has no idea whether counters live in a `ConcurrentHashMap` or a Redis
hash. That's what makes swapping backends a config change, not a rewrite.

### Storage abstraction

```java
public interface RateLimitStore {
    RateLimitCounters incrementAndGet(String key, long windowSeconds, long requestCost, long tokenCost);
    RateLimitCounters peek(String key, long windowSeconds);
}
```

Both implementations satisfy the same contract:

| | InMemoryRateLimitStore | RedisRateLimitStore |
|---|---|---|
| Data structure | `ConcurrentHashMap<String, WindowEntry>` | Redis hash (`HINCRBY`) |
| Atomicity | `ConcurrentHashMap#compute` (per-key lock) | Lua script (server-side atomic) |
| Expiry | Timestamp check + scheduled sweep | Native Redis `TTL` |
| Scope | Single JVM | Shared across instances |
| Bean activated by | `ratelimiter.storage.type=memory` (default) | `ratelimiter.storage.type=redis` |

### Windowing algorithm

Fixed window, per client, starting at the client's *first* request rather
than clock-aligned minute boundaries — identical behavior in both stores:

1. First request for a client creates a window and starts a `windowSeconds`
   countdown (in-memory: a stored start timestamp; Redis: `EXPIRE`).
2. Every subsequent request in that window atomically increments the
   request and token counters.
3. Once `now >= windowStart + windowSeconds`, the next request rolls the
   window over and starts fresh.
4. A request is **allowed** only if both the post-increment request count
   and token count are within policy limits; otherwise it's denied but the
   counters still reflect the attempt (standard fixed-window semantics —
   the same approach used by GitHub/Stripe-style APIs).

## Running it

Requires JDK 21 and Maven (or use the included `./mvnw` wrapper).

```bash
mvn spring-boot:run
```

The API is now on `http://localhost:8080` — no other setup needed.

### Run the tests

```bash
mvn test
```

Includes concurrency tests (50 threads × 100 increments hammering the same
client key) that assert no increments are lost, plus service-layer tests for
the allow/deny decision logic and controller tests for HTTP status mapping.

### Docker

```bash
docker build -t ai-rate-limiter .
docker run -p 8080:8080 ai-rate-limiter
```

or with Compose (in-memory storage, no Redis container started):

```bash
docker compose up
```

## API Reference

### `POST /api/v1/rate-limit/check`

Consumes one request + the given token estimate from the client's current
window.

**Request**
```json
{ "clientId": "demo-client", "estimatedTokens": 250 }
```
`estimatedTokens` is optional (defaults to 0) for RPM-only callers.

**Response — `200 OK`** (allowed) or **`429 Too Many Requests`** (denied):
```json
{
  "allowed": true,
  "clientId": "demo-client",
  "requestLimit": 60,
  "remainingRequests": 59,
  "tokenLimit": 10000,
  "remainingTokens": 9750,
  "resetInSeconds": 47,
  "resetAtEpochSeconds": 1735680047,
  "reason": null
}
```
When denied, `reason` is one of `RPM_EXCEEDED`, `TPM_EXCEEDED`, or
`RPM_AND_TPM_EXCEEDED`.

### `GET /api/v1/rate-limit/status/{clientId}`

Read-only quota lookup — does **not** consume any allowance. Same response
shape as above.

### Error responses

Validation failures (missing/blank `clientId`, negative tokens, malformed
JSON) return `400` with:
```json
{
  "timestamp": "2026-07-09T10:15:30Z",
  "status": 400,
  "error": "Validation Failed",
  "message": "Request payload is invalid",
  "details": ["clientId: clientId must not be blank"]
}
```

A sample Postman collection is at
[`postman/AI-Aware-Rate-Limiter.postman_collection.json`](postman/AI-Aware-Rate-Limiter.postman_collection.json).
Or with curl:

```bash
curl -X POST http://localhost:8080/api/v1/rate-limit/check \
  -H "Content-Type: application/json" \
  -d '{"clientId":"demo-client","estimatedTokens":250}'

curl http://localhost:8080/api/v1/rate-limit/status/demo-client
```

## Configuration

All in `src/main/resources/application.properties`:

```properties
ratelimiter.storage.type=memory          # memory | redis
ratelimiter.window-seconds=60
ratelimiter.default-policy.requests-per-minute=60
ratelimiter.default-policy.tokens-per-minute=10000

# Optional per-client overrides
ratelimiter.client-policies.premium-client.requests-per-minute=600
ratelimiter.client-policies.premium-client.tokens-per-minute=200000

# In-memory housekeeping (ignored when storage.type=redis)
ratelimiter.cleanup.enabled=true
ratelimiter.cleanup.interval-ms=30000

# Only read when storage.type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Every property can also be set as an environment variable (Spring's relaxed
binding), e.g. `RATELIMITER_STORAGE_TYPE=redis`.

## Switching to Redis

The Redis implementation already ships in this repo — it's just inactive by
default. To turn it on:

1. **Start Redis.** Easiest via the bundled compose profile:
   ```bash
   docker compose --profile redis up
   ```
2. **Flip one property** (`application.properties`, env var, or `-D` flag):
   ```properties
   ratelimiter.storage.type=redis
   spring.data.redis.host=localhost
   spring.data.redis.port=6379
   ```
3. **Nothing else changes.** `RedisConfig` (`@ConditionalOnProperty`) wires
   up a `StringRedisTemplate` and loads
   `src/main/resources/scripts/rate_limit.lua` as a `RedisScript`.
   `RedisRateLimitStore` implements the exact same `RateLimitStore`
   interface `InMemoryRateLimitStore` does, so `RateLimiterServiceImpl`,
   the controller, and every test above the storage layer are completely
   unaffected.

### Why a Lua script instead of separate `INCR` calls?

A naive "read count, check limit, increment, set TTL if new" sequence is
**not atomic** across separate Redis round trips — two concurrent requests
could both read the same pre-increment count and both decide they're under
the limit. `scripts/rate_limit.lua` runs the whole sequence as one
server-side script, so Redis executes it as a single atomic operation, the
same guarantee `ConcurrentHashMap#compute` provides for the in-memory store,
but now valid across multiple application instances sharing one Redis.

```lua
-- KEYS[1]=key, ARGV[1]=windowSeconds, ARGV[2]=requestCost, ARGV[3]=tokenCost
-- returns { requestCount, tokenCount, ttlSecondsRemaining }
```

See [`src/main/resources/scripts/rate_limit.lua`](src/main/resources/scripts/rate_limit.lua)
for the full script.

### Extending further

Adding a third backend (e.g. DynamoDB, Hazelcast) means: implement
`RateLimitStore`, annotate the bean with a matching
`@ConditionalOnProperty(havingValue = "...")`, and add a new
`ratelimiter.storage.type` value. No other class needs to change.

## Design notes / trade-offs

- **Fixed window, not sliding log/leaky bucket** — chosen for O(1) memory
  per client and trivial Redis parity, at the cost of allowing up to ~2x
  the limit right at a window boundary. Documented here rather than hidden;
  swapping in a sliding-window algorithm only touches the store
  implementations, not the interface.
- **Counters increment even when denied** — matches how most public APIs
  (GitHub, Stripe) behave: the attempt still "costs" against the window so
  clients can't retry-storm their way around the limit.
- **No SQL, ORM, Kafka, or Kubernetes** — deliberately out of scope per the
  brief; this is a focused, embeddable rate-limiting component, not a
  platform.
