# Redis

Nexus uses a single shared Redis instance for session state and future ephemeral
concerns (rate limiting, API-key cache, permission snapshots, heartbeats, queues).
PostgreSQL remains the durable source of truth. Redis holds bounded ephemeral
state; caches and projections are derivable, while losing active sessions logs
users out. See `docs/adr/0008-shared-redis-and-revokable-sessions.md` for the full
rationale and per-capability failure rules.

## Local development

Redis is declared in `compose.yaml` and is started by Docker Compose. Spring Boot's
Docker Compose support (`spring-boot-docker-compose`) manages its lifecycle at dev
runtime.

```bash
docker compose up -d redis
```

Configuration (`apps/api/src/main/resources/application.properties`):

- `spring.data.redis.url=${NEXUS_REDIS_URL:redis://localhost:6379}`
- `spring.data.redis.connect-timeout=2s`, `spring.data.redis.timeout=2s`
- indexed Spring Session repository and namespace `nexus:session`, configured in
  `admin/application/configuration/PanelSessionConfiguration`
- `nexus.session.timeout` / `NEXUS_SESSION_TIMEOUT` controls the real Redis session
  inactivity interval (applied via a `SessionRepositoryCustomizer`).
- `nexus.session.cookie.*` / `NEXUS_SESSION_COOKIE_*` set name, path, http-only,
  same-site, secure and max-age of the Spring Session cookie (`CookieSerializer`).
- `nexus.session.revocation.resubmit-interval` /
  `NEXUS_SESSION_REVOCATION_RESUBMIT_INTERVAL` (default `60s`) bounds the periodic
  re-delivery of revocation events that could not be applied due to a Redis outage.
- `nexus.session.revocation.resubmit-batch-size` (default `100`),
  `nexus.session.revocation.resubmit-max-in-flight` (default `10`) and
  `nexus.session.revocation.resubmit-min-age` (default `15s`) bound each periodic
  re-delivery of `NexusAccountSessionsRevocationRequested`. Other event types are never
  re-delivered by the republisher. At startup, revocations are re-delivered immediately
  (`minAge` zero).

The local service binds only to `127.0.0.1:6379`, persists data under the
`nexus-redis-data` volume, and runs with:

- `appendonly yes`, `appendfsync everysec`
- `maxmemory 96mb`, `maxmemory-policy noeviction`
- `notify-keyspace-events Egx`
- container memory limit `160m`

## Production

- Do **not** publish the Redis port. Reach it over a private network only.
- Provide credentials and optionally TLS via `NEXUS_REDIS_URL`
  (e.g. `rediss://:password@host:6379`).
- Keep `noeviction`. Sessions must never be silently evicted by cache pressure.
- Monitor memory: warn at 70%, alert at 85%.

## Operational notes

- The app can boot without a Redis connection, but the readiness health group
  (`/actuator/health/readiness`) reports `DOWN` and any session-dependent request
  returns `503` with `code: redis_unavailable` (see
  `shared/web/RedisUnavailableFilter`). Protected endpoints never resolve as
  anonymous.
- There is no in-memory fallback. Redis is required for panel sessions.
- The initial deployment that moves panel sessions from the servlet container to
  Redis invalidates all pre-existing in-memory sessions; users must sign in again.
- Session revocation triggered by account lifecycle (suspension, deactivation, removal
  of `instanceAdmin`) is reliable: Spring Modulith persists the revocation event in
  PostgreSQL and `PanelSessionRevocationRepublisher` re-delivers incomplete
  publications at startup and on a bounded schedule. The operation is idempotent, so a
  Redis outage right after commit does not lose the revocation.
- No separate logical databases are used. Concerns are separated by key namespace,
  each owned by its module. Every ephemeral key must carry a TTL, and every
  collection or stream must have an explicit cardinality bound.
