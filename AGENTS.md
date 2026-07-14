# Nexus Repository Guide

This repository is a monorepo for Nexus: a personal control plane for projects, API keys, isolated auth, permission management, modules, and future SDKs.

Use this file as the source of truth for where new code and documents should live.

## Repository Layout

```text
.
├── apps/
│   ├── api/
│   └── web/
├── packages/
│   └── nexus-spring-boot-sdk/
├── docs/
│   ├── adr/
│   ├── api/
│   ├── auth/
│   ├── deployment/
│   └── permissions/
├── examples/
├── scripts/
├── gradle/
├── compose.yaml
├── settings.gradle
└── AGENTS.md
```

## apps/api

Spring Boot backend for Nexus.

This is the main API and control plane. It owns:

- project registry,
- API keys,
- module activation,
- Nexus accounts and instance administration,
- project memberships,
- project users,
- permissions,
- auth/OAuth,
- audit,
- heartbeat,
- future backend modules such as notify, storage, vault, config, metrics, backups, and document generation.

Run backend quality checks from the repository root:

```bash
./gradlew build
```

### apps/api/src/main/java/dev/unzor/nexus

Main Java source package.

Keep Nexus as a modular monolith. Prefer package/module boundaries over premature microservices.

### shared

Common code used across modules.

Put here:

- API response helpers,
- common DTO primitives,
- error/problem details,
- typed identifiers and narrow cross-module contracts,
- shared validation helpers,
- persistence utilities,
- security primitives used by multiple modules,
- time/clock helpers.

Do not put module-owned entities or project-specific business logic here.

Entities remain in the module that owns their lifecycle and invariants, even when
other modules need to reference them. Cross-module persistence should store typed
IDs such as `NexusAccountId`, `ProjectId`, or `ProjectUserId`, not JPA associations
to entities owned by another module. Obtain additional data through public
application services or domain events; never access another module's repository
directly.

### Redis

Nexus uses a single shared Redis instance. PostgreSQL is the only durable source of
truth for business and security records. Redis holds bounded ephemeral state:
most values are derivable, while active sessions are intentionally not
reconstructible and their loss must only log users out. See
`docs/adr/0008-shared-redis-and-revokable-sessions.md`.

Panel sessions are backed by Spring Session Redis (`RedisIndexedSessionRepository`,
namespace `nexus:session`), indexed by account. Session serialization is JDK, so
`NexusAccountPrincipal` is `Serializable` and implements `CredentialsContainer`; never
store JPA entities in session. The handler erases the password hash before the
`SecurityContext` is persisted, so the serialized principal never retains the hash. The
management API exposes only `nexus.sessionPublicId`, never the internal Spring Session
ID or the `JSESSIONID` value. Session attributes are populated in
`admin/infrastructure/security/PanelAuthenticationSuccessHandler`; the account index
resolver, attribute names, the configurable inactivity timeout
(`SessionRepositoryCustomizer` reading `NEXUS_SESSION_TIMEOUT`) and the cookie serializer
(applying `nexus.session.cookie.*`) live in
`admin/application/configuration/PanelSessionConfiguration`. Listing and revocation go
through `admin/application/service/PanelSessionService`.

Revocation triggered by account lifecycle (suspension, deactivation, removal of
`instanceAdmin`) is reliable. `NexusAccount` (a Spring Data `AbstractAggregateRoot`)
registers `NexusAccountSessionsRevocationRequested`, which lives in
`admin/domain/events` so that `domain` does not depend on `application`. The event is
only published when the aggregate is saved through the repository within the
transaction. Spring Modulith persists the publication in PostgreSQL; if Redis fails
after commit, `PanelSessionRevocationRepublisher` (`admin/application/events`)
re-delivers incomplete publications using `ResubmissionOptions` filtered to
`NexusAccountSessionsRevocationRequested` only, at startup (immediately, `minAge` zero)
and on a bounded schedule (`minAge` configurable, default 15s; batch size and
in-flight limit configurable). Other event types are never re-delivered by it. The
revocation operation is idempotent.

Redis is required for session- and security-dependent operations; there is no
in-memory fallback. Only exceptions whose cause chain contains an unambiguous
Redis/Lettuce signal are converted to `503` `redis_unavailable` by
`shared/web/RedisUnavailableFilter`, which runs before `SessionRepositoryFilter`.
Generic data-access timeouts unrelated to Redis propagate normally. The app may boot
without Redis, but the readiness health group is `DOWN`.

Rules for any module that touches Redis:

- No separate logical databases. Separate concerns by key namespace, each owned by
  the module that introduced it.
- Every ephemeral key must carry a TTL.
- Bound key cardinality and stream length; shared Redis uses `noeviction`, so an
  unbounded feature can make security-critical writes fail.
- Never use `KEYS` or global scans on the request path.
- No generic business-logic Redis client in `shared`. Each module owns its keys and
  its adapters.


### admin

Nexus dashboard identity and instance-level administration.

Put here:

- Nexus accounts,
- instance administrator grants,
- Nexus account login/session logic,
- instance-level authorization,
- Nexus account persistence,
- admin API controllers.

A `NexusAccount` is a person who can access the Nexus dashboard. Most Nexus
accounts are not instance administrators. Instance administration is represented
by the `instanceAdmin` flag on the account, not by a separate account type or
extensible role model.

Nexus accounts are not the same as project users. Do not merge their credentials,
sessions, or persistence models.

Panel security lives in `admin/application/configuration/PanelSecurityConfiguration`
(`@Order(3)`): `/panel/**`, `/api/panel/**`, form login at `/panel/login`, CSRF,
and `NexusAccountUserDetailsService` scoped to that chain only (not a global
`UserDetailsService` bean).

Panel sessions use server-side `JSESSIONID` state. Controllers obtain the current
account through `@AuthenticationPrincipal NexusAccountPrincipal`; they must not
parse cookies or trust account IDs supplied by the client. The default session
timeout and persistent cookie lifetime are seven days and can be overridden with
`NEXUS_SESSION_TIMEOUT` and `NEXUS_SESSION_COOKIE_MAX_AGE`.

Reserve `/admin/**` and `/api/admin/**` for a future instance-administration
surface restricted to Nexus accounts with `instanceAdmin = true`.

### projects

Project lifecycle and metadata.

Put here:

- project creation,
- project listing,
- project status,
- project slug/name/description,
- project-level metadata,
- project isolation rules.
- project memberships for Nexus accounts,
- project ownership and dashboard management roles.

A project represents one application/product boundary such as F-Shop or GarageLab.

Use `ProjectMembership` to connect a `NexusAccount` to a project with a role such
as `OWNER`, `ADMIN`, or `MEMBER`. Project administration is a membership concern,
not an instance administrator role.

### apikeys

Project API key management and validation.

Put here:

- API key generation,
- hashing and validation,
- key scopes,
- key rotation/revocation,
- `X-Nexus-Api-Key` authentication,
- API-key-related security filters.

API keys identify projects. Never store full API key secrets in plain text.

### modules

Per-project module activation.

Put here:

- module definitions,
- project module enable/disable state,
- module config,
- module gate checks.

Endpoints belonging to a module should fail when that module is disabled for the calling project.

### audit

Central audit trail.

Put here:

- audit event model,
- audit writing service,
- audit querying API,
- actor/outcome/resource metadata.

Audit sensitive actions such as login attempts, API key changes, module changes, permission changes, and token/session revocation.

### registry

App registration and heartbeat.

Put here:

- heartbeat endpoint,
- app instance status,
- online/offline calculation,
- app version/runtime metadata.

This module answers: which app instances for a project are alive?

### permissions

Project-scoped authorization flags.

Put here:

- permission catalog,
- permission declaration sync,
- roles,
- user role assignments,
- direct user permission assignments,
- wildcard resolver,
- permission check API,
- permission snapshot API.

MVP supports positive permissions only:

- exact permission, e.g. `orders.cancel`,
- namespace wildcard, e.g. `orders.*`,
- global wildcard, e.g. `*`.

Do not implement negative permissions until the rules and tests are explicit.

### identity

Project-isolated user identity and OAuth.

Put here:

- project users,
- OAuth clients,
- Spring Authorization Server integration,
- project issuer handling,
- JWT/JWKS logic,
- refresh/session handling,
- project auth flows.

Project users are isolated per project. The same email in two projects is two different users.
They are end users authenticated through a project's OAuth/OIDC realm and do not
gain access to the Nexus dashboard.

OAuth clients, authorizations, and consents are persisted in PostgreSQL (JDBC).
Bootstrap client configuration: `identity/application/configuration/NexusOAuthBootstrapProperties`
and `OidcRegisteredClientBootstrap`. The end-user surface is fully Next.js + JSON under
`/api/p/{slug}/**` (`ProjectEndUserSecurityConfiguration`, `@Order(4)`): per-project
multi-issuer OAuth (ADR-0016, `CompositeRegisteredClientRepository`), functional
`ProjectUser` login + email-verify + password-reset, TOTP MFA (step-up + enrollment +
recovery codes), consent, and per-user session management (list/revoke). CSRF is enabled
on the `/api/p/**` chain; the OAuth machine endpoints (`/oauth2/{token,introspect,revoke}`)
are exempt.

### notify

Notification service.

Put here:

- notification requests,
- email delivery,
- templates,
- notification history,
- notification delivery audit.

Notify is important, but it should not own auth or permission rules.

### Per-Module Package Convention

Most backend modules should follow this shape:

```text
module/
├── api/
│   ├── controller/
│   ├── dto/
│   └── requests/
├── application/
│   ├── configuration/
│   ├── events/
│   ├── observability/
│   └── service/
├── domain/
│   ├── entity/
│   ├── enums/
│   └── exception/
├── persistence/
│   └── repository/
└── infrastructure/
    └── interceptor/
```

Use:

- `api/controller/` for HTTP controllers exposed by the module.
- `api/dto/` for response DTOs and API-facing read models.
- `api/requests/` for request payloads accepted by controllers.
- `application/*Application.java` for module bootstrapping, module-level orchestration, or startup hooks.
- `application/configuration/` for module-local application configuration.
- `application/events/` for application events published or handled by the module.
- `application/observability/` for metrics, traces, health contributors, and module-specific instrumentation.
- `application/service/` for use cases and orchestration services.
- `domain/entity/` for domain entities and value objects.
- `domain/enums/` for domain enums.
- `domain/exception/` for module-specific domain exceptions.
- `persistence/repository/` for JPA repositories and database-facing adapters.
- `infrastructure/interceptor/` for technical interceptors that belong to the module.

Only add specialized folders such as `security/`, `oauth/`, `resolver/`, `snapshot/`, or `mail/` when the module genuinely needs them.

## apps/api/src/main/resources

Backend resources.

Put here:

- `application.properties` or `application.yml`,
- profile-specific configuration,
- Flyway migrations under `db/migration`,
- logging configuration if needed.

Prefer Flyway migrations for schema changes. Do not rely on Hibernate auto-DDL for real schema evolution.

## docs/adr

Architecture Decision Records.

Put here:

- decisions that shape the system,
- tradeoffs that future contributors should not have to rediscover,
- security and data consistency decisions,
- deployment or infrastructure decisions.

ADRs should explain context, decision, and consequences. Do not use ADRs for routine implementation notes.

## apps/api/src/test

Backend tests.

Put here:

- unit tests,
- Spring context tests,
- repository tests,
- integration tests,
- Testcontainers-based database tests.

Keep Docker-dependent tests explicit. The default `./gradlew build` should remain reliable for local development unless the project intentionally requires Docker for all builds.

## apps/web

Next.js admin dashboard.

Put here:

- admin UI routes,
- project management screens,
- API key management UI,
- module toggles,
- permission catalog and role management UI,
- heartbeat/status views,
- audit browsing UI.

Recommended source layout:

```text
apps/web/src/
├── app/
├── components/
├── features/
├── lib/
└── styles/
```

Keep the dashboard operational and clear. Avoid marketing-page structure for the admin interface.

Organize frontend API calls using the same layered convention as GarageLab:

- `lib/api/routes.ts` owns backend URLs and route builders.
- `lib/api/client.ts` owns shared fetch behavior, response parsing, credentials, and typed API errors.
- `lib/api/csrf.ts` owns Nexus panel CSRF handling.
- `features/<feature>/api.ts` owns feature operations and API-facing types.
- Pages and components call feature APIs; they should not build backend URLs or perform raw `fetch` calls.

Group API routes hierarchically by surface and resource. Each resource should
expose `root`, dynamic selectors such as `byId`, and resource-specific actions:

```ts
apiRoutes.panel.accounts.root
apiRoutes.panel.accounts.byId(accountId)
apiRoutes.panel.accounts.changePassword(accountId)
apiRoutes.panel.session.me
```

Do not place unrelated resource routes at the same flat level. Encode dynamic
path segments with `encodeURIComponent`.

Do not copy GarageLab's Next.js proxy/BFF routes by default. Nexus currently calls
the backend directly from the browser because its panel authentication uses the
API-hosted session cookie and cookie-to-header CSRF flow.

## packages

Reusable packages, SDKs, and framework integrations that are consumed by applications outside the Nexus API itself.

Packages are not product modules. Product behavior belongs in `apps/api`; integration helpers belong here.

### packages/nexus-spring-boot-sdk

One-stop Java SDK / Spring Boot starter for applications that integrate with Nexus.
A single dependency autoconfigures both halves: **security** (OIDC login, local JWT
validation, `@perm` permission authz, RP-initiated + back-channel logout) and
**management** (heartbeat, permission declaration sync, permission snapshot cache,
notify) from the `nexus.*` properties. Root Gradle module
(`:packages:nexus-spring-boot-sdk`), library (bootJar disabled), zero dependency
on the backend (Nexus reached only over HTTP). The `examples/spring-client-app` is a
root module too and consumes this starter.

Put here:

- Nexus Java client,
- auto-configuration (management + Spring Security OAuth2),
- API key configuration,
- heartbeat client,
- permission declaration sync,
- permission snapshot cache,
- typed service clients.

The SDK should consume Nexus HTTP APIs. It must not become the source of truth for behavior.

Good package responsibilities:

- make Nexus easy to call from Spring apps,
- expose typed clients for Nexus APIs,
- cache permission snapshots safely,
- register app-declared permissions,
- add auto-configuration for API keys and base URLs.

Bad package responsibilities:

- owning database schema,
- resolving permissions differently from Nexus,
- duplicating Nexus business rules,
- depending directly on `apps/api` internals.

Future packages may include SDKs for TypeScript, Python, Go, or Kotlin.

## docs

Human-readable documentation.

Put here:

- architecture docs,
- technical specs,
- auth explanations,
- permission system docs,
- API docs,
- deployment docs,
- operational runbooks.

Suggested folders:

- `docs/api/`: API contracts and examples.
- `docs/auth/`: OAuth, JWT, JWKS, Spring Authorization Server notes.
- `docs/permissions/`: permission model, wildcard rules, examples.
- `docs/deployment/`: Docker/server deployment notes.

## examples

Example apps that consume Nexus.

### examples/spring-client-app

Reference Spring Boot 4 app connected to Nexus — a root Gradle module
(`:examples:nexus-spring-client-app`) that consumes the
`nexus-spring-boot-sdk` and demos every feature: OIDC login, resource-server
JWT authz (`@perm`), permission snapshot, heartbeat, permission declaration,
notify, and back-channel logout. See its `README.md` for the capability matrix
and the end-to-end walkthrough.

Use examples to prove SDK ergonomics and document real integration patterns.

Examples should remain small and focused.

## scripts

Repository helper scripts.

Put here:

- local dev commands,
- build helpers,
- migration helpers,
- release helpers,
- formatting/check scripts,
- one-shot maintenance helpers that are safe to run repeatedly.

Scripts should be small, readable, and safe to run from the repository root.

Do not put application logic in scripts. If code is required at runtime, it belongs in `apps/api`, `apps/web`, or a package.

## gradle

Gradle wrapper files.

Do not edit wrapper files manually unless intentionally upgrading Gradle.

## Versioning

All modules publish under the **`dev.unzor.nexus`** groupId umbrella (verifiable on
Maven Central via the `unzor.dev` domain — once `dev.unzor` is verified, the whole
`dev.unzor.nexus.*` subtree is covered). The `dev.unzor.nexus` prefix is shared across
this and other projects to avoid coordinate collisions.

Module coordinates:

- **`dev.unzor.nexus:nexus-api`** — the self-hosted server (`apps/api`), package `dev.unzor.nexus.*`.
- **`dev.unzor.nexus.sdk:nexus-spring-boot-sdk`** — the client SDK/starter (`packages/nexus-spring-boot-sdk`), package `dev.unzor.nexus.sdk` (groupId = base package).
- **`dev.unzor.nexus.example:nexus-spring-client-app`** — the reference app (`examples/spring-client-app`).

**Each module is versioned independently.** There is no shared release train: in a
given iteration, bump only the module(s) whose code actually changed. Each module
keeps its own `version` in its `build.gradle`.

Version scheme is **Semantic Versioning** (`MAJOR.MINOR.PATCH`):

- **MAJOR** — incompatible / breaking change.
- **MINOR** — new, backwards-compatible feature.
- **PATCH** — bugfix / hotfix.

Until the public surfaces stabilize, modules stay on **`0.x`** (pre-1.0); cut `1.0.0`
per module only when that module's API is considered stable.

Only **`dev.unzor.nexus.sdk:nexus-spring-boot-sdk`** is published to Maven Central;
the server and the example keep internal `0.0.x-SNAPSHOT` versions. When you change a
module in an iteration, update its `version` per the rules above; do not bump
unchanged modules.

## Root Files

### settings.gradle

Gradle multi-project configuration.

Currently registers:

```text
apps/api -> nexus-api
examples/spring-client-app -> nexus-spring-client-app
```

Add future Gradle subprojects here when needed.

### README.md

General project introduction and quickstart.

### AGENTS.md

Repository instructions for agents and contributors.

Update this file whenever the structure changes meaningfully.

## Development Rules

- Keep API contracts versioned.
- Keep project isolation explicit.
- API keys identify projects.
- Multiple API keys per project are first-class.
- Default authorization result is deny.
- Do not delete permission assignments during declaration sync.
- Keep permissions positive-only until negative permissions are deliberately designed.
- Keep Nexus as a modular monolith until there is real pressure to split services.
- Prefer OpenAPI-defined HTTP contracts over SDK-only behavior.
- Run `./gradlew build` after backend structural changes.

## Remote development over zrok (HTTPS tunnels)

**Trigger:** if the user says they are developing remotely, over zrok, from
another device/browser, needs the app on HTTPS, or asks to "expose the
backend/frontend with a tunnel", work through this runbook. It gives the backend
and frontend each a public HTTPS URL so cross-site session cookies work. (This
flow used ngrok; it switched to zrok.)

### Why remote-dev is a distinct mode

Plain localhost HTTP uses `SameSite=Lax; Secure=false` cookies and
localhost-only CORS. Over HTTPS tunnels the browser (on the frontend tunnel)
fetches the backend tunnel cross-site, so the panel session cookie must be
`SameSite=None; Secure` and CORS must allow the frontend origin. That is the
`remote-dev` Spring profile (`application-remote-dev.properties`). Running the
backend in the default profile under tunnels makes every cross-site request —
including the CORS preflight — return `403`.

### One-time zrok setup

Install zrok and run `zrok enable <account-token>` once (the token comes from
your zrok account and is stored outside the repo). There is no per-tunnel config
file — each tunnel is a `zrok share public` process.

### Bring-up

1. **Check existing tunnels/instances first** (the user often has them running):
   ```bash
   zrok overview              # lists active shares + their public URLs
   pgrep -fl "zrok share"
   docker ps                  # postgres/redis are shared infra
   ```
   In `zrok overview`, find the shares targeting `http://localhost:8080` and
   `http://localhost:3000` and reuse their `*.shares.zrok.io` URLs. If both
   exist, skip to step 3. (`zrok status` does NOT list share URLs.)

2. **Start the tunnels** (before the app — the URLs feed its env), two processes:
   ```bash
   zrok share public http://localhost:8080 --headless &
   zrok share public http://localhost:3000 --headless &
   zrok overview              # copy the two *.shares.zrok.io URLs
   ```
   Let `$BACK` = the backend (8080) share URL, `$FRONT` = the frontend (3000)
   share URL.

3. **Wire the frontend env.** The app is run on the host (not docker), and
   **host-run Next.js does NOT read the repo-root `.env`** — it reads
   `apps/web/.env.local`:
   ```dotenv
   # apps/web/.env.local
   NEXT_PUBLIC_NEXUS_API_BASE_URL=<$BACK>
   ```
   Restart the frontend (`npm run dev`) after editing `.env.local`. (The
   repo-root `.env` is read only by `docker compose`'s `web` service; if you run
   the frontend via compose instead, set `NEXT_PUBLIC_NEXUS_API_BASE_URL`,
   `NEXUS_FRONTEND_BASE_URL`, and `NEXUS_ALLOWED_DEV_ORIGINS` there and
   `docker compose up -d web`.)

4. **Run the backend in remote-dev.** It is not a docker service; it is a Spring
   Boot app launched from the shell (or IntelliJ). Kill whatever holds `:8080`,
   then (long-running — run detached/backgrounded):
   ```bash
   SPRING_PROFILES_ACTIVE=remote-dev \
   NEXUS_FRONTEND_BASE_URL=<$FRONT> \
   NEXUS_ALLOWED_DEV_ORIGINS=<$FRONT> \
   SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT=START_ONLY \
   ./gradlew :apps:nexus-api:bootRun
   ```
   Notes:
   - Task path is `:apps:nexus-api:bootRun` — the module is renamed in
     `settings.gradle` (`project(':apps:api').name = 'nexus-api'`), so
     `:apps:api:bootRun` does not resolve.
   - `SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT=START_ONLY` prevents a backend shutdown
     from running `docker compose down` and tearing down the shared stack.
   - The backend reads these from the **shell env**, not the repo-root `.env`.

### Verify

```bash
curl -s "$BACK/actuator/health/readiness"   # expect {"status":"UP"}
# remote-dev cookie must be SameSite=None; Secure
curl -sI "$BACK/login" | grep -i set-cookie
# CORS must allow the frontend origin on a panel path (expect access-control-allow-origin: $FRONT)
curl -s -D - -o /dev/null "$BACK/api/panel/v1/csrf" -H "Origin: $FRONT" | grep -i access-control
```
A `403` on `/login` or `/api/projects` for an **anonymous** request is normal
(protected endpoints reject unauthenticated access) — it is not a tunnel/CORS
problem. (zrok has no interstitial page, so no special curl header is needed,
unlike ngrok free.)

**curl `200` is not proof the page renders.** Next.js dev blocks cross-origin
dev resources (HMR) unless the tunnel host is in `allowedDevOrigins`. If the page
returns HTML but stays blank/stuck in a real browser, `next.config.ts` derives
`allowedDevOrigins` from `NEXUS_ALLOWED_DEV_ORIGINS`; confirm `[HMR] connected`
in the browser console after a hard refresh.

### Refresh / tear-down

- Stop the tunnels by killing the `zrok share public` processes (`pkill -f "zrok share"`).
- zrok public shares are **not reserved** by default — their tokens change every
  time they are recreated. When they do, refresh `apps/web/.env.local`, the
  backend's `NEXUS_FRONTEND_BASE_URL`/`NEXUS_ALLOWED_DEV_ORIGINS`, and (if you
  use docker compose) the repo-root `.env`, then restart the app. For **stable**
  URLs, reserve them once with `zrok reserve public` and run the shares with
  `--share <name>`.
