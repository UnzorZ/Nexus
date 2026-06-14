# ADR-0008: Shared Redis for Ephemeral Coordination

Status: Accepted

## Context

Nexus needs shared, low-latency state for several concerns:

- revocable panel sessions,
- rate limiting,
- short-lived permission snapshots,
- short-lived API-key validation results,
- online/offline heartbeat projections, and
- asynchronous work queues.

Keeping panel sessions in a servlet container prevents horizontal scaling,
cross-node revocation and account-level session management. Deploying a different
store for every future concern would increase baseline memory, operational work and
failure modes without providing useful isolation at the current scale.

PostgreSQL remains the durable source of truth for accounts, projects, API keys,
permissions, audit data, heartbeat history and business records. Redis is not a
second business database. Most Redis values must be derivable from PostgreSQL.
Active sessions are the exception: they are intentionally ephemeral and cannot be
reconstructed. Losing them logs users out; it must never grant access.

## Decision

Nexus will initially operate one shared Redis deployment, separated by owned key
namespaces rather than logical Redis databases. This is an operational deployment
decision, not permission for modules to share business logic or manipulate each
other's keys.

The local profile uses Redis `8.8.0-alpine`, AOF with `appendfsync everysec`,
`maxmemory 96mb`, `maxmemory-policy noeviction`, and a `160m` container limit.
Production may use a managed or highly available Redis deployment through
`NEXUS_REDIS_URL`.

Redis is required only for capabilities whose contract depends on it. There is no
process-local fallback for sessions, distributed rate limits, heartbeats or queues.
Cache consumers may fall back to PostgreSQL when their rules below explicitly allow
it.

## Global Invariants

- Use a single Redis database. Every concern owns a namespace such as
  `nexus:<module>:<purpose>:...`; Spring Session keeps `nexus:session`.
- Every cache, limiter, heartbeat and deduplication key has a finite TTL.
- Bounded streams use an explicit `MAXLEN`. Unbounded collections are forbidden.
- Never use `KEYS` or a global keyspace scan on a request path.
- Modules access Redis through module-owned adapters. `shared` may contain technical
  connection/error primitives, but no generic Redis business repository.
- Modules must not read or delete another module's keys.
- New values use an explicit, versioned serialization contract. JDK serialization
  is limited to Spring Session and requires rolling-deployment compatibility.
- Sensitive values are minimized. Never store raw passwords, password hashes, raw
  API keys, access tokens or refresh tokens unless a separate ADR explicitly
  requires it.
- `noeviction` is deliberate: memory pressure must fail writes visibly instead of
  silently evicting security state. Every feature must bound cardinality and expose
  metrics before production rollout.

## Panel Sessions

Panel authentication uses server-side Spring Session Redis:

- `RedisIndexedSessionRepository` uses namespace `nexus:session`.
- Sessions are indexed by stable Nexus account ID without scanning keyspace.
- The browser receives only an HTTP-only `JSESSIONID`. Session-management APIs use a
  separate random `nexus.sessionPublicId` and never expose the cookie value or
  Spring Session's internal ID.
- Stored application attributes are limited to the account ID, public session ID
  and a truncated user agent. Spring Security stores its `SecurityContext`, whose
  serializable principal must contain only the minimum data required after login
  and must not retain the password hash.
- The inactivity timeout and persistent-cookie lifetime default to seven days.
  `NEXUS_SESSION_TIMEOUT` controls the Redis-backed server session and
  `NEXUS_SESSION_COOKIE_MAX_AGE` controls the browser cookie. Cookie security
  attributes must be applied to Spring Session's `CookieSerializer`, not merely to
  the servlet container.
- `GET /api/panel/v1/sessions`,
  `DELETE /api/panel/v1/sessions/{publicSessionId}` and
  `DELETE /api/panel/v1/sessions` are scoped to the authenticated account. Unknown
  and foreign public IDs both return `404`.
- Revoking the current session invalidates the `HttpSession`, clears the
  `SecurityContext` and expires the cookie. Revoking all sessions includes the
  current one.
- Password changes, suspension, deactivation and removal of a global authority
  revoke every session after the PostgreSQL transaction commits. Adding a global
  authority must either revoke existing sessions or document that reauthentication
  is required before it becomes effective.
- Revocation is idempotent. Lifecycle-triggered revocation must be delivered
  reliably and retried if Redis is temporarily unavailable.

Redis session access fails closed. When Redis cannot load or persist a session, the
request returns `503` with `code: redis_unavailable`; it never becomes an anonymous
or authenticated request by fallback. Requests without a session cookie may still
return their normal public or `401` response without consulting Redis.

## Rate Limiting

- Use namespace `nexus:rate-limit`.
- Counters and expiry changes are atomic through a Lua script or an equivalent
  single atomic Redis operation.
- Keys include the correct security dimension for the endpoint, such as normalized
  login identifier plus source address, project ID, or API-key fingerprint.
- Rejections return `429` and `Retry-After`.
- Authentication and security-sensitive limits fail closed with `503` when Redis is
  unavailable. A future non-security limiter may choose another policy only in its
  own ADR.

## API-Key Validation Cache

- Cache only successful validations, for at most 15 seconds.
- Derive the cache key from an HMAC fingerprint of the presented secret. Never put
  the raw key in a Redis key, value or log.
- A cache miss or Redis failure falls back to authoritative PostgreSQL validation.
- Rotation and revocation evict the cached fingerprint synchronously after the
  database change commits.
- Cached data includes only the minimum project/key identity and status needed by
  the authentication flow.

## Permission Snapshots

- Use namespace `nexus:permissions:snapshot`.
- Snapshots have a maximum TTL of 30 seconds and include an `authzVersion` owned by
  PostgreSQL-backed authorization state.
- A stale, expired or version-mismatched snapshot never authorizes a request.
- On cache miss or Redis failure, resolve from PostgreSQL when the endpoint can do so
  safely; otherwise deny or return `503`. Never allow from stale data.
- Permission mutations increment the authoritative version and evict affected
  snapshots after commit.

## Heartbeats

- PostgreSQL stores registered instances, metadata and any history Nexus promises
  to retain.
- Redis stores only the online projection, with a default 90-second TTL refreshed by
  heartbeat.
- Online status is computed from key presence or expiry time. Keyspace expiry
  notifications may support observability but are never required for correctness.
- A heartbeat that cannot update Redis returns `503`; it is not acknowledged as
  accepted while the online projection remains stale.

## Queues

- Use Redis Streams, not Pub/Sub, for work that must survive consumer disconnects.
- Use consumer groups, explicit acknowledgement after successful processing,
  bounded retries, a dead-letter stream and a mandatory bounded `MAXLEN`.
- Business commands that require durable delivery write a PostgreSQL outbox record
  in the same transaction as the business change. The relay retries publishing to
  Redis, so a temporary Redis outage does not lose committed work.
- A direct stream-only enqueue has no durable fallback and returns `503` if Redis
  cannot accept it.
- Consumers must be idempotent because delivery is at least once.

## Availability And Operations

- Redis connection and command timeouts default to two seconds.
- Readiness includes PostgreSQL and Redis. Liveness does not depend on Redis, so an
  external outage does not create a restart loop.
- Local Redis binds only to `127.0.0.1`. Production Redis is reachable only through
  a private network and uses authentication and TLS where supported.
- Warn at 70% Redis memory usage and alert at 85%. Monitor rejected writes,
  connection failures, latency, session count, limiter cardinality, stream lag and
  dead-letter depth.
- Capacity is reviewed before enabling each new Redis-backed feature. A shared
  deployment may be split when measured load, availability requirements or noisy
  neighbor behavior justify it.
- Backups are not a substitute for PostgreSQL. Restoring Redis may recover some
  ephemeral state, but losing Redis is handled by invalidating sessions, rebuilding
  caches/projections and replaying durable outbox work.

## Consequences

- Panel sessions work across API nodes and can be listed or revoked centrally.
- The first Redis-backed deployment invalidates existing in-memory sessions.
- Redis becomes part of the availability path for sessions and other explicitly
  dependent capabilities, requiring monitoring and production hardening.
- One small shared service keeps the initial resource cost low, while strict
  namespaces, bounds and ownership rules preserve a later path to separate
  deployments.
- PostgreSQL remains authoritative for durable security and business data; a Redis
  loss can cause logout or temporary unavailability, but not unauthorized access or
  permanent business-data loss.
