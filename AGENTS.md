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
│   └── nexus-spring-boot-starter/
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
- admin accounts,
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
- shared validation helpers,
- persistence utilities,
- security primitives used by multiple modules,
- time/clock helpers.

Do not put project-specific business logic here.

### admin

Nexus dashboard/admin identity.

Put here:

- Nexus admin accounts,
- instance admin and project admin roles,
- admin login/session logic,
- admin-facing authorization,
- admin account persistence,
- admin API controllers.

Admin accounts are not the same as project users.

### projects

Project lifecycle and metadata.

Put here:

- project creation,
- project listing,
- project status,
- project slug/name/description,
- project-level metadata,
- project isolation rules.

A project represents one application/product boundary such as F-Shop or GarageLab.

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

### notify

Notification service.

Put here:

- notification requests,
- Telegram delivery,
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

Only add specialized folders such as `security/`, `oauth/`, `resolver/`, `snapshot/`, or `telegram/` when the module genuinely needs them.

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

## packages

Reusable packages, SDKs, and framework integrations that are consumed by applications outside the Nexus API itself.

Packages are not product modules. Product behavior belongs in `apps/api`; integration helpers belong here.

### packages/nexus-spring-boot-starter

Java SDK / Spring Boot starter for applications that integrate with Nexus.

Put here:

- Nexus Java client,
- auto-configuration,
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

Future sample Spring Boot app connected to Nexus.

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

## Root Files

### settings.gradle

Gradle multi-project configuration.

Currently registers:

```text
apps/api -> nexus-api
```

Add future Gradle subprojects here when needed, such as the Java SDK.

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
