# Nexus

Nexus is a self-hosted control plane for projects: API keys, project-scoped
identity, permissions, module activation, audit logs, notifications, encrypted
secrets, configuration, metrics, and runtime registry/heartbeat.

It is built as a modular monolith: one Spring Boot backend with strong module
boundaries, one Next.js dashboard, PostgreSQL as the durable database, and Redis
for revokable sessions and bounded ephemeral state.

> Status: active development. The project is useful for local exploration, but
> production deployment still needs a real secrets/keystore setup, an explicit
> license, and the usual operational hardening.

## What Nexus Provides

- **Control-plane accounts**: Nexus accounts, instance administration, panel
  login, CSRF, Redis-backed sessions, and session revocation.
- **Projects**: project registry, memberships, ownership, and project status.
- **API keys**: project-identifying keys, hashed secrets, scopes, runtime
  authentication, and instance-token handshakes for high-frequency calls.
- **Permissions**: project permission catalog, roles, direct assignments,
  wildcard resolution, checks, and snapshots.
- **Identity**: project users, per-project OAuth/OIDC realms, OAuth clients,
  JWT/JWKS, consent, logout, and persisted authorizations.
- **Modules**: per-project module enablement and request gating.
- **Audit**: central audit trail for sensitive actions.
- **Registry**: app registration and heartbeat/liveness tracking.
- **Notify**: templates, project/instance SMTP settings, and email delivery.
- **Vault**: encrypted project secrets with project-level master key support.
- **Config and metrics**: project configuration values and append-only metrics.

## Repository Layout

```text
.
├── apps/
│   ├── api/   # Spring Boot backend
│   └── web/   # Next.js dashboard
├── docs/
│   ├── adr/
│   ├── auth/
│   ├── deployment/
│   └── modules/
├── examples/
├── gradle/
├── compose.yaml
├── settings.gradle
└── AGENTS.md
```

The backend Gradle project is named `:apps:nexus-api` in
`settings.gradle`.

## Requirements

- Java 21
- Docker + Docker Compose
- Node.js 22 if running the dashboard outside Docker
- npm if running the dashboard outside Docker

## Quick Start

From the repository root:

```bash
# Starts PostgreSQL, Redis, and the Next.js dev container.
docker compose up -d

# Starts the Spring Boot API.
./gradlew :apps:nexus-api:bootRun
```

Open:

- Dashboard: http://localhost:3000
- API: http://localhost:8080
- Readiness: http://localhost:8080/actuator/health/readiness
- OpenAPI UI: http://localhost:8080/swagger-ui.html

The first registered Nexus account becomes the initial instance admin.

### Local Frontend Instead Of Docker

If you prefer running Next.js directly on the host:

```bash
docker compose up -d postgres redis

cd apps/web
npm install
npm run dev
```

In another shell, from the repository root:

```bash
./gradlew :apps:nexus-api:bootRun
```

The host-run frontend reads `apps/web/.env.local`, not the repository-root
`.env`.

## Configuration

Local defaults are intentionally convenient:

- PostgreSQL: `nexus / nexus / nexus`
- Redis: `redis://localhost:6379`
- OAuth bootstrap client secret: `changeme-local-dev`
- JWT signing keystore: `classpath:keystore/dev-jwk.p12`
- Vault master key: `nexus-dev-vault-master-key-do-not-use-in-prod`

These values are for local development only. The backend fails closed outside
explicit dev/test profiles when development identity or vault secrets are used.

Production-like deployments should set at least:

```bash
NEXUS_DATASOURCE_URL=jdbc:postgresql://...
NEXUS_DATASOURCE_USERNAME=...
NEXUS_DATASOURCE_PASSWORD=...
NEXUS_REDIS_URL=redis://...
NEXUS_FRONTEND_BASE_URL=https://...
NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET=...
NEXUS_OAUTH_JWK_KEYSTORE_LOCATION=file:/etc/nexus/jwt-keystore.p12
NEXUS_OAUTH_JWK_KEYSTORE_PASSWORD=...
NEXUS_OAUTH_JWK_KEY_ALIAS=...
NEXUS_OAUTH_JWK_KEY_PASSWORD=...
NEXUS_VAULT_MASTER_KEY=...
```

See [JWT signing keys](docs/deployment/jwt-signing-keys.md) and
[Redis](docs/deployment/redis.md) for more detail.

## Remote Development

Remote browser testing over HTTPS tunnels needs different cookie and CORS
settings. Use the `remote-dev` Spring profile and expose both the backend and
frontend with HTTPS tunnel URLs.

The full zrok runbook lives in [AGENTS.md](AGENTS.md), under
`Remote development over zrok (HTTPS tunnels)`.

The essential backend shape is:

```bash
SPRING_PROFILES_ACTIVE=remote-dev \
NEXUS_FRONTEND_BASE_URL=<frontend-https-url> \
NEXUS_ALLOWED_DEV_ORIGINS=<frontend-https-url> \
SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT=START_ONLY \
./gradlew :apps:nexus-api:bootRun
```

## Tests And Quality

Backend:

```bash
./gradlew :apps:nexus-api:test
./gradlew build
```

Frontend:

```bash
cd apps/web
npm run lint
npm run build
```

Some backend tests use Testcontainers. Keep Docker running when executing the
full suite.

## Architecture Notes

- Nexus is a **modular monolith** using Spring Modulith.
- PostgreSQL is the durable source of truth.
- Redis is shared, bounded ephemeral infrastructure for sessions and related
  security state.
- API keys identify projects, not human users.
- Nexus accounts and project users are intentionally separate identities.
- Authorization is fail-closed by default.
- API key secrets and user passwords are never stored in plain text.

Useful starting points:

- [Technical spec](docs/nexus-technical-spec.md)
- [ADR-0001: Nexus is the source of truth](docs/adr/0001-nexus-source-of-truth.md)
- [ADR-0002: Modular monolith with Spring Modulith](docs/adr/0002-modular-monolith-with-spring-modulith.md)
- [Accounts and project users](docs/auth/accounts-and-project-users.md)
- [Identity module](docs/modules/identity.md)

## Security Notes

This repository includes development-only secrets and keystores so the project
can boot locally without extra setup. They are deliberately named as dev
defaults and are rejected outside explicit development/test profiles where
appropriate.

Before running Nexus outside local development:

- generate a real JWT signing keystore;
- set a real OAuth bootstrap client secret;
- set a real Vault master key;
- use managed or locked-down PostgreSQL and Redis;
- configure HTTPS, secure cookies, and production CORS;
- review module exposure and API key scopes.

## License

No license has been selected yet. Until a license is added, treat the code as
source-available for review, not as broadly licensed open source.
