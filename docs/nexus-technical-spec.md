# Nexus Technical Specification

Version: 0.1  
Status: Draft for implementation planning  
Primary audience: project owner, future contributors, future SDK implementers

## 1. Executive Summary

Nexus is a personal control plane for backend projects. It centralizes common platform capabilities that would otherwise be duplicated across applications: project registry, API keys, isolated project authentication, permission management, notifications, audit, module configuration, and future shared infrastructure services.

The initial deployment target is personal use, but the architecture must remain clean enough to become open source later. Nexus will run without Kubernetes, preferably with Docker or a local server installation. The first supported application stack is Java/Spring Boot, but all public integration contracts must be HTTP/OpenAPI-first so SDKs can later be created for other languages.

The system follows this central rule:

> Nexus is the source of truth. If Nexus is unavailable, dependent authentication and authorization flows fail closed.

## 2. Confirmed Decisions

- Nexus starts as a new project from scratch.
- Backend stack: Spring Boot.
- Backend architecture: modular monolith, preferably with Spring Modulith boundaries.
- Authorization server: Spring Authorization Server for production.
- Learning goal: document and understand the low-level OAuth2/OIDC/JWT mechanics behind Spring Authorization Server.
- Frontend: React/Next.js dashboard as a separate client consuming Nexus APIs.
- Database: PostgreSQL.
- Deployment: Docker or local process on a server; no Kubernetes.
- Quality gate for backend implementation: `./gradlew build`.
- Each Nexus project is identified to Nexus by one or more API keys.
- Multiple API keys per project are supported from the beginning.
- No environment split in MVP; environments such as dev/staging/prod may be added later.
- Each project has isolated authentication and isolated users.
- Nexus manages explicit permissions for each project.
- Apps expose permission strings; Nexus stores, displays, assigns, and resolves them.
- Backend applications are the only expected direct consumers of Nexus in MVP.
- Permission system supports only positive permissions in MVP.
- Permission negation is a future feature.
- Permission snapshots with short TTL are supported to reduce Nexus load.

## 3. Product Definition

### 3.1 What Nexus Is

Nexus is a platform service that provides shared operational capabilities to multiple backend projects.

Nexus manages:

- projects,
- project API keys,
- project modules,
- isolated project users,
- OAuth clients,
- permission catalogs,
- roles and assignments,
- authorization checks,
- audit events,
- health and heartbeat status,
- future modules such as Notify, Storage, Vault, Config, Metrics, Backups, and Document Generation.

### 3.2 What Nexus Is Not

Nexus is not:

- a generic business application framework,
- a replacement for domain logic inside apps,
- a microservice platform,
- a Kubernetes operator,
- a frontend authorization library for MVP,
- a multi-tenant SaaS product in the first version,
- a place to hardcode project-specific business rules.

### 3.3 Core Design Principle

Apps declare what they can do. Nexus decides who can do it.

Example:

- F-Shop declares `orders.cancel`.
- Nexus assigns `orders.cancel` to a role or user.
- F-Shop asks Nexus whether user `usr_123` can perform `orders.cancel`.
- F-Shop still owns the actual order cancellation logic.

## 4. Conceptual Model

```text
Nexus Instance
├── Nexus Accounts
├── Instance Administrator Grants
├── Projects
│   ├── Project Memberships
│   ├── Project API Keys
│   ├── Enabled Modules
│   ├── OAuth Clients
│   ├── Project Users
│   ├── Permission Catalog
│   ├── Roles
│   ├── Permission Assignments
│   ├── Heartbeat State
│   └── Audit Events
└── Global Configuration
```

## 5. Domain Terms

### 5.1 Nexus Instance

A single running installation of Nexus. For personal use, there will usually be one production instance at a stable domain such as `nexus.unzor.xyz`.

### 5.2 Nexus Account

A person account used to access the Nexus dashboard, create projects, and manage
projects through explicit memberships.

Important rules:

- A Nexus account is not a project user.
- Most Nexus accounts are not instance administrators.
- Instance administration is represented by `NexusAccount.instanceAdmin`, not a
  separate account type or extensible role catalog.
- An instance administrator can manage all projects and global settings.
- Project access is granted through project memberships.

### 5.3 Project Membership

A relationship that grants a Nexus account access to manage a specific project
from the dashboard.

Recommended membership roles:

- `OWNER`: controls the project and its memberships.
- `ADMIN`: manages project configuration and resources.
- `MEMBER`: has limited dashboard access defined by project policy.

The account that creates a project receives its first `OWNER` membership.
Project administration is not represented by the `instanceAdmin` flag.

### 5.4 Project

A security and configuration boundary representing one application or product, such as F-Shop, GarageLab, or F-Drive.

A project owns:

- API keys,
- project users,
- OAuth clients,
- permission catalog,
- roles,
- module configuration,
- audit log entries.

### 5.5 API Key

A machine credential used by a backend app to identify itself to Nexus.

Important rules:

- An API key belongs to exactly one project.
- A project may have multiple API keys.
- API keys are never stored in plain text.
- API keys have scopes.
- API keys can be disabled, rotated, expired, and audited.

### 5.6 Project User

A user account inside a specific project auth realm.

Important rules:

- Users are isolated by project.
- The same email can exist in different projects as different users.
- A project user is not a Nexus account and cannot access the Nexus dashboard.
- A project user may receive roles and direct permissions inside that project.

### 5.7 OAuth Client

An OAuth2/OIDC client registered under a project.

Examples:

- `fshop-web`
- `fshop-backend`
- `garagelab-admin`

MVP consumers are backend applications, but OAuth clients should still be modeled cleanly for future browser or mobile flows.

### 5.8 Module

A feature area that can be enabled or disabled per project.

Core system capabilities are always present internally, but project-level access to modules is configurable.

Candidate modules:

- `identity`
- `permissions`
- `registry`
- `notify`
- `audit`
- `storage`
- `vault`
- `config`
- `metrics`
- `backup`

### 5.9 Permission

A string flag declared by an app and managed by Nexus.

Examples:

- `orders.read`
- `orders.cancel`
- `products.write`
- `admin.dashboard.access`
- `*`

Permissions are positive-only in MVP.

## 6. Architecture Overview

```text
                    ┌───────────────────────────┐
                    │       Next.js Admin UI    │
                    │  dashboard / project mgmt │
                    └──────────────┬────────────┘
                                   │ HTTPS
                                   ▼
┌──────────────────────────────────────────────────────────────┐
│                         Nexus API                            │
│                    Spring Boot Modulith                      │
│                                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ Admin/Auth   │ │ Project Core │ │ API Key Security    │  │
│  └──────────────┘ └──────────────┘ └─────────────────────┘  │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ Identity     │ │ Permissions  │ │ Registry/Heartbeat  │  │
│  └──────────────┘ └──────────────┘ └─────────────────────┘  │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────────────┐  │
│  │ Audit        │ │ Notify       │ │ Future Modules      │  │
│  └──────────────┘ └──────────────┘ └─────────────────────┘  │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
                       ┌──────────────┐
                       │ PostgreSQL   │
                       └──────────────┘

         ┌─────────────────────────────────────────────┐
         │ Java apps with nexus-spring-boot-starter    │
         │ API key + heartbeat + authz snapshot cache  │
         └─────────────────────────────────────────────┘
```

## 7. Backend Modular Structure

Recommended package structure:

```text
com.unzor.nexus
├── NexusApplication.java
├── shared
│   ├── api
│   ├── errors
│   ├── persistence
│   ├── security
│   └── time
├── admin
│   ├── account
│   ├── session
│   └── web
├── projects
│   ├── domain
│   ├── api
│   └── persistence
├── apikeys
│   ├── domain
│   ├── api
│   └── security
├── modules
│   ├── domain
│   ├── gate
│   └── api
├── identity
│   ├── users
│   ├── oauth
│   ├── sessions
│   └── tokens
├── permissions
│   ├── catalog
│   ├── roles
│   ├── assignments
│   ├── resolver
│   └── snapshot
├── registry
│   ├── heartbeat
│   └── status
├── audit
│   ├── api
│   ├── application
│   ├── domain
│   └── persistence
└── notify
    ├── mail
    ├── templates
    └── delivery
```

Spring Modulith should be used to keep module boundaries explicit. Cross-module communication should happen through public application services or domain events, not direct repository access.

Audit is a first-class module. Other modules should not write directly to the audit database tables. They should publish meaningful events or call a narrow audit application service with a stable audit command.

Module-owned entities remain in their owning module even when several modules
reference them. Cross-module persistence stores typed identifiers such as
`NexusAccountId`, `ProjectId`, and `ProjectUserId`; it must not create JPA
associations to entities owned by another module. `shared` may contain these
stable identifiers and narrow contracts, but not account or membership entities.

## 8. Database and Persistence

Use PostgreSQL as the primary database and Flyway or Liquibase for migrations.

Recommended migration tool: Flyway.

All tables should include:

- `id`,
- `created_at`,
- `updated_at`,
- where relevant, `deleted_at` or `disabled_at`,
- optimistic locking where concurrent admin edits are likely.

IDs should be stable and opaque. UUIDs are acceptable for MVP. Public IDs may later use readable prefixes such as `prj_`, `usr_`, `key_`.

## 9. Core Data Model

### 9.1 Nexus Accounts and Control-Plane Access

```text
nexus_accounts
- id
- email
- password_hash
- display_name
- status
- mfa_enabled
- last_login_at
- created_at
- updated_at
```

```text
project_memberships
- id
- project_id
- nexus_account_id
- role
- status
- created_at
- updated_at
```

Rules:

- `nexus_accounts.email` is globally unique.
- `nexus_accounts.instance_admin = true` grants global instance access.
- Project roles are `OWNER`, `ADMIN`, or `MEMBER` and belong to
  `project_memberships`.
- Unique `(project_id, nexus_account_id)`.
- Every project must retain at least one active `OWNER`.
- Nexus accounts and project users are separate identities.
- `NexusAccount` is owned by `admin`; `ProjectMembership` is owned by `projects`.

The concrete entities, status transitions, Spring Security principals, and
module boundaries are documented in
[`docs/auth/accounts-and-project-users.md`](auth/accounts-and-project-users.md).

### 9.2 Projects

```text
projects
- id
- slug
- name
- description
- status
- public_base_url nullable
- created_at
- updated_at
```

Rules:

- `slug` is unique.
- Project isolation is based on `project_id`.
- Environment separation is out of scope for MVP.

### 9.3 API Keys

```text
project_api_keys
- id
- project_id
- name
- key_prefix
- key_hash
- scopes
- status
- expires_at nullable
- last_used_at nullable
- created_by_account_id nullable
- created_at
- updated_at
```

Rules:

- Store only `key_prefix` and `key_hash`.
- Show the full key only once when created.
- Use constant-time comparison for key validation.
- API key scopes are additive.
- Disabled keys must be rejected immediately.
- Expired keys must be rejected.

Recommended API key format:

```text
nxs_<projectSlug>_<randomSecret>
```

The project slug helps humans identify the key, but the database lookup should rely on a safe prefix/hash strategy, not trust the slug blindly.

### 9.4 Project Modules

```text
project_modules
- id
- project_id
- module_key
- enabled
- config_json
- created_at
- updated_at
```

Rules:

- One row per project/module.
- Module checks happen before executing module-specific APIs.
- Disabled modules return `403 module_disabled`.

### 9.5 Project Users

```text
project_users
- id
- project_id
- email
- username nullable
- password_hash
- display_name
- status
- email_verified_at nullable
- last_login_at nullable
- authz_version
- created_at
- updated_at
```

Rules:

- Unique `(project_id, email)`.
- Users do not cross project boundaries.
- `authz_version` increments when effective permissions may have changed.
- `ProjectUser` does not implement `UserDetails`; `ProjectUserPrincipal` adapts
  it to Spring Security while preserving `project_id`.

### 9.6 OAuth Clients

```text
project_oauth_clients
- id
- project_id
- client_id
- client_secret_hash nullable
- name
- redirect_uris
- post_logout_redirect_uris
- grant_types
- scopes
- require_pkce
- status
- created_at
- updated_at
```

Rules:

- OAuth clients are project-scoped.
- Client IDs should be unique globally for operational simplicity.
- Redirect URIs must match exactly.
- PKCE should be required for public clients.

### 9.7 Permission Catalog

```text
project_permissions
- id
- project_id
- key
- label
- description nullable
- source
- enabled
- deprecated
- missing_from_last_sync
- last_declared_at nullable
- created_at
- updated_at
```

Allowed sources:

- `WEB`
- `YAML`
- `CODE`
- `OPENAPI`
- `SYSTEM`

Rules:

- Unique `(project_id, key)`.
- Permission keys are immutable once created.
- Metadata can be updated.
- Missing permissions from app sync are not deleted automatically.
- Deprecated permissions remain visible for audit/history.

### 9.8 Roles

```text
project_roles
- id
- project_id
- key
- label
- description nullable
- system
- created_at
- updated_at
```

```text
project_role_permissions
- id
- project_id
- role_id
- permission_key
- created_at
```

Rules:

- Unique `(project_id, key)`.
- Role keys are stable.
- Role permissions reference permission keys, not necessarily permission IDs, to support wildcard entries.

### 9.9 User Assignments

```text
project_user_roles
- id
- project_id
- user_id
- role_id
- created_at
```

```text
project_user_permissions
- id
- project_id
- user_id
- permission_key
- created_at
```

Rules:

- Direct user permissions are positive-only in MVP.
- Role permissions and direct user permissions are additive.
- Every assignment mutation increments the target user's `authz_version`.

### 9.10 Audit Events

```text
audit_events
- id
- project_id nullable
- trace_id nullable
- actor_type
- actor_id nullable
- action
- resource_type
- resource_id nullable
- outcome
- ip_address nullable
- user_agent nullable
- metadata_json
- created_at
```

Actor types:

- `NEXUS_ACCOUNT`
- `PROJECT_USER`
- `API_KEY`
- `SYSTEM`

Audit must be written for:

- Nexus account login success/failure,
- project creation/update,
- API key creation/disable/delete,
- module enable/disable,
- permission declaration,
- role changes,
- permission assignment changes,
- auth login success/failure,
- token revocation,
- sensitive module actions.

Audit event payloads must be intentionally small. Store enough metadata to understand what happened, but never store full API keys, passwords, refresh tokens, authorization codes, or raw JWTs.

### 9.11 Heartbeat

```text
project_heartbeats
- id
- project_id
- api_key_id
- instance_id
- app_name
- app_version nullable
- status
- metadata_json
- last_seen_at
- created_at
- updated_at
```

Rules:

- Heartbeat is tied to the API key that reported it.
- Multiple app instances can report for the same project.
- Offline status is derived from `last_seen_at` and configured timeout.

## 10. API Authentication

Nexus has two API categories:

### 10.1 Panel API

Used by the Next.js dashboard.

Recommended path:

```text
/api/panel/v1/...
```

Authentication:

- Nexus account login via HTTP-only session cookie (`JSESSIONID`).
- Server-side session timeout and persistent cookie lifetime default to seven
  days; deployments may override `NEXUS_SESSION_TIMEOUT` and
  `NEXUS_SESSION_COOKIE_MAX_AGE`.
- Form login at `{api}/panel/login`; Next.js `/login` redirects there with optional `continue`.
- Panel API at `/api/panel/**` returns **401** when unauthenticated (no HTML redirect).
- Panel HTML at `/panel/**` redirects unauthenticated users to `/panel/login`.
- CSRF via cookie `XSRF-TOKEN` and header `X-XSRF-TOKEN` on mutating requests.
- Logout: `POST /api/panel/v1/session/logout` (204) or `POST /panel/logout` for HTML navigation.
- `/admin/**` and `/api/admin/**` are reserved for a future `INSTANCE_ADMIN`-only surface.
- Keep dashboard auth separate from project OAuth (`/p/{projectSlug}/**`).

### 10.2 Project API

Used by backend applications.

Recommended path:

```text
/api/v1/...
```

Authentication:

```http
X-Nexus-Api-Key: nxs_fshop_...
```

Rules:

- API key identifies the project.
- API key scopes determine what the backend may call.
- Project ID should not be trusted from request bodies when an API key is present.
- If project is disabled, reject the request.
- If module is disabled, reject module-specific requests.

## 11. Error Format

Use `application/problem+json` style responses.

Example:

```json
{
  "type": "https://docs.nexus.local/errors/module-disabled",
  "title": "Module disabled",
  "status": 403,
  "code": "module_disabled",
  "detail": "The permissions module is disabled for this project.",
  "traceId": "01J..."
}
```

Common codes:

- `invalid_api_key`
- `api_key_disabled`
- `api_key_expired`
- `missing_scope`
- `project_disabled`
- `module_disabled`
- `permission_denied`
- `validation_error`
- `resource_not_found`
- `conflict`
- `rate_limited`
- `internal_error`

## 12. Project and API Key Contracts

### 12.1 Create Project

Panel API:

```http
POST /api/panel/v1/projects
Content-Type: application/json
```

Request:

```json
{
  "slug": "f-shop",
  "name": "F-Shop",
  "description": "Ecommerce project"
}
```

Response:

```json
{
  "id": "prj_123",
  "slug": "f-shop",
  "name": "F-Shop",
  "status": "active"
}
```

### 12.2 Create API Key

Panel API:

```http
POST /api/panel/v1/projects/{projectId}/api-keys
Content-Type: application/json
```

Request:

```json
{
  "name": "fshop-backend",
  "scopes": [
    "registry:heartbeat",
    "permissions:declare",
    "permissions:check",
    "authz:snapshot",
    "notify:send"
  ],
  "expiresAt": null
}
```

Response:

```json
{
  "id": "key_123",
  "name": "fshop-backend",
  "prefix": "nxs_f-shop_abc123",
  "secret": "nxs_f-shop_abc123.full-secret-visible-once",
  "scopes": [
    "registry:heartbeat",
    "permissions:declare",
    "permissions:check",
    "authz:snapshot",
    "notify:send"
  ]
}
```

Rules:

- `secret` is only returned once.
- Subsequent reads show metadata only.
- Key creation writes an audit event.

## 13. Registry and Heartbeat

### 13.1 Heartbeat

Project API:

```http
POST /api/v1/registry/heartbeat
X-Nexus-Api-Key: nxs_fshop_...
Content-Type: application/json
```

Required scope:

```text
registry:heartbeat
```

Request:

```json
{
  "instanceId": "fshop-api-main-01",
  "appName": "F-Shop API",
  "appVersion": "1.4.0",
  "status": "up",
  "metadata": {
    "javaVersion": "21",
    "springProfile": "prod"
  }
}
```

Response:

```json
{
  "projectId": "prj_123",
  "receivedAt": "2026-05-30T14:00:00Z",
  "nextHeartbeatInSeconds": 30
}
```

Rules:

- SDK sends heartbeat every 30 seconds by default.
- Nexus marks an instance offline if no heartbeat is received for 90 seconds by default.
- Offline detection can later trigger Notify.

## 14. Permission System

### 14.1 Permission Philosophy

Nexus should behave like a project-level permission authority:

- Apps declare permission flags.
- Admins assign permissions to roles and users.
- Apps ask Nexus whether a user has a permission.
- Nexus returns an answer based on explicit assignments.

Nexus does not know project business rules.

### 14.2 Permission Key Format

Recommended grammar:

```text
permission = segment("." segment)* | wildcard
segment    = lowercase letters, numbers, hyphen, underscore
wildcard   = "*" or prefix ".*"
```

Examples:

```text
orders.read
orders.cancel
products.write
admin.dashboard.access
orders.*
*
```

Recommended convention:

```text
<resource>.<action>
<area>.<resource>.<action>
```

Examples:

```text
orders.read
orders.refund
admin.users.invite
billing.invoices.export
```

### 14.3 MVP Matching Rules

Positive permissions only.

A user is allowed if any effective permission matches:

- exact match: `orders.cancel` matches `orders.cancel`,
- namespace wildcard: `orders.*` matches `orders.cancel`,
- global wildcard: `*` matches everything.

No negative permissions in MVP.

### 14.4 Future Matching Rules

Future versions may support:

```text
-orders.cancel
```

Potential precedence:

1. explicit deny,
2. explicit allow,
3. wildcard deny,
4. wildcard allow,
5. default deny.

This must not be added until there is a clear test suite and UI explanation, because negation makes permission resolution much easier to misunderstand.

### 14.5 Effective Permissions

Effective permissions for a user:

```text
direct user permissions
UNION role permissions from all assigned roles
```

MVP has no inheritance between roles.

Future role inheritance may be considered, but it should not be part of v1.

### 14.6 Permission Declaration Sources

Permissions can be registered through:

1. Dashboard/manual web entry.
2. Local `application.yml`.
3. Hardcoded declaration in Java code.

All declarations go into the same project permission catalog.

### 14.7 YAML Declaration

Example:

```yaml
nexus:
  permissions:
    - key: orders.read
      label: Ver pedidos
      description: Permite listar y consultar pedidos
    - key: orders.cancel
      label: Cancelar pedidos
      description: Permite cancelar pedidos abiertos
    - key: admin.dashboard.access
      label: Acceso al panel admin
```

### 14.8 Code Declaration

Example:

```java
@Bean
NexusPermissionDeclaration permissions() {
    return NexusPermissionDeclaration.of(
        Permission.of("orders.read", "Ver pedidos"),
        Permission.of("orders.cancel", "Cancelar pedidos"),
        Permission.of("admin.dashboard.access", "Acceso al panel admin")
    );
}
```

### 14.9 Declaration Sync API

Project API:

```http
PUT /api/v1/permissions/declarations
X-Nexus-Api-Key: nxs_fshop_...
Content-Type: application/json
```

Required scope:

```text
permissions:declare
```

Request:

```json
{
  "source": "YAML",
  "declarationId": "fshop-api-default",
  "permissions": [
    {
      "key": "orders.read",
      "label": "Ver pedidos",
      "description": "Permite listar y consultar pedidos"
    },
    {
      "key": "orders.cancel",
      "label": "Cancelar pedidos",
      "description": "Permite cancelar pedidos abiertos"
    }
  ]
}
```

Response:

```json
{
  "projectId": "prj_123",
  "created": ["orders.read"],
  "updated": ["orders.cancel"],
  "markedMissing": ["products.write"],
  "ignored": []
}
```

Rules:

- Sync is a merge, not a destructive replace.
- Existing assignments are never deleted by sync.
- Missing permissions are marked as `missing_from_last_sync = true`.
- Admins can later deprecate or delete unused permissions manually.
- Web-created permissions are not removed by app sync.

### 14.10 Permission Check API

Project API:

```http
POST /api/v1/authz/check
X-Nexus-Api-Key: nxs_fshop_...
Content-Type: application/json
```

Required scope:

```text
permissions:check
```

Request:

```json
{
  "userId": "usr_123",
  "permission": "orders.cancel"
}
```

Response:

```json
{
  "allowed": true,
  "userId": "usr_123",
  "permission": "orders.cancel",
  "matchedBy": "orders.*",
  "authzVersion": 42
}
```

Rules:

- Default is deny.
- Unknown user returns deny or `404` based on SDK configuration; MVP should prefer deny for safer behavior.
- Unknown permission returns deny and may include warning metadata.

### 14.11 Permission Snapshot API

Project API:

```http
GET /api/v1/authz/users/{userId}/snapshot
X-Nexus-Api-Key: nxs_fshop_...
```

Required scope:

```text
authz:snapshot
```

Response:

```json
{
  "userId": "usr_123",
  "projectId": "prj_123",
  "authzVersion": 42,
  "roles": ["manager"],
  "permissions": [
    "orders.read",
    "orders.cancel",
    "products.*"
  ],
  "expiresAt": "2026-05-30T14:05:00Z"
}
```

Rules:

- Default TTL: 30 seconds.
- Recommended configurable range: 5 to 300 seconds.
- SDK resolves permissions locally while snapshot is valid.
- If snapshot expires and Nexus is unavailable, SDK denies permission.
- Any assignment mutation increments `authzVersion`.
- Future optimization may support conditional requests using `If-None-Match` or version checks.

## 15. Identity and Auth

### 15.1 Identity Scope

Each project has isolated users and OAuth clients.

There is no global end-user identity in MVP.

The same email in two projects represents two separate project users.

### 15.2 Authorization Server

Use Spring Authorization Server for production implementation.

**Implemented:** JDBC-backed `RegisteredClientRepository`, `OAuth2AuthorizationService`, and `OAuth2AuthorizationConsentService` (PostgreSQL). A technical bootstrap client is seeded idempotently via `nexus.oauth.bootstrap.*`; it is not used by the dashboard.

Nexus should document the low-level concepts it relies on:

- `RegisteredClientRepository`: stores OAuth clients.
- `OAuth2AuthorizationService`: stores authorization codes, access tokens, refresh tokens.
- `OAuth2AuthorizationConsentService`: stores user consent if used.
- `OAuth2TokenGenerator`: creates tokens.
- `JWKSource`: exposes signing keys.
- Authorization endpoint: starts login/consent.
- Token endpoint: exchanges code/refresh/client credentials.
- JWK Set endpoint: exposes public keys.
- OIDC discovery endpoint: tells clients where endpoints and keys are.

### 15.3 Project Issuer Model

Each project should have its own issuer.

Recommended issuer:

```text
https://nexus.example.com/p/{projectSlug}
```

Example:

```text
https://nexus.unzor.xyz/p/f-shop
```

Recommended endpoints:

```text
GET  /p/{projectSlug}/.well-known/openid-configuration
GET  /p/{projectSlug}/oauth2/authorize
POST /p/{projectSlug}/oauth2/token
GET  /p/{projectSlug}/oauth2/jwks
POST /p/{projectSlug}/oauth2/revoke
GET  /p/{projectSlug}/userinfo
```

JWT claims:

```json
{
  "iss": "https://nexus.unzor.xyz/p/f-shop",
  "sub": "usr_123",
  "aud": "fshop-api",
  "exp": 1770000000,
  "iat": 1769996400,
  "scope": "openid profile",
  "project_id": "prj_123",
  "authz_version": 42,
  "permissions": ["orders.*", "orders.read"]
}
```

Rules:

- Tokens must never cross project boundaries.
- Project ID must be included as a claim.
- The user's effective permission keys are embedded as the `permissions` claim
  (wildcards `orders.*` / `*` verbatim, ADR-0003) in the access token, ID token
  and `/userinfo`, so a resource server can authorize locally from the JWT.
  This is an **optimistic** snapshot: a role change does not reach a
  locally-validating consumer until the token's `exp` (access tokens are
  short-lived, 10 min — see §15.2 / ADR-0016). For revocation-sensitive
  decisions, validate via `/oauth2/introspect` (it enforces `authz_version`) or
  call the permission snapshot/check API for authoritative authorization. The
  snapshot/check API remains the source of truth for fresh decisions.
- Roles may optionally be included later, but Nexus remains the permission source of truth.

### 15.4 Auth Flow MVP

Recommended production-first flow:

- Authorization Code Flow with PKCE.
- Refresh tokens for trusted clients where appropriate.
- Strict redirect URI validation.
- JWT access tokens signed by Nexus.
- JWKS endpoint for token validation.

For Java backend apps:

- Backend initiates login redirect.
- Nexus authenticates user in project realm.
- Nexus redirects back to app.
- App exchanges code for tokens.
- App validates token or asks Nexus depending on integration mode.
- App asks Nexus for permission snapshot when it needs authorization decisions.

### 15.5 Fail-Closed Behavior

If Nexus is unavailable:

- new login fails,
- token refresh fails,
- permission check fails,
- expired permission snapshot fails,
- secret/config reads fail unless explicitly cached by a future module.

This is intentional because Nexus is the source of truth.

## 16. Module System

### 16.1 Module Gate

Every project-level API must pass:

1. API key authentication.
2. Project status check.
3. API key scope check.
4. Module enabled check, when endpoint belongs to a module.
5. Endpoint-specific authorization.

### 16.2 Core vs Project Modules

Always-on internal capabilities:

- project management,
- API key validation,
- audit write path,
- admin dashboard access.

Project-configurable modules:

- identity,
- permissions,
- registry,
- notify,
- storage,
- vault,
- config,
- metrics,
- backup,

Recommended MVP default:

- enable `registry`,
- enable `permissions`,
- enable `identity` when project auth is needed.

## 17. Admin Dashboard

### 17.1 Dashboard Goals

The dashboard is the control surface for Nexus.

It must allow:

- Nexus account login,
- project listing,
- project creation/editing,
- project membership management,
- module enable/disable,
- API key creation/revocation,
- permission catalog management,
- role management,
- user permission assignment,
- heartbeat/status visibility,
- audit browsing.

### 17.2 Route Map

Recommended Next.js routes:

```text
/register
/login          # redirects to API /panel/login?continue=…
/dashboard
/projects
/projects/new
/projects/[projectId]
/projects/[projectId]/overview
/projects/[projectId]/api-keys
/projects/[projectId]/modules
/projects/[projectId]/users
/projects/[projectId]/users/[userId]
/projects/[projectId]/permissions
/projects/[projectId]/roles
/projects/[projectId]/oauth-clients
/projects/[projectId]/heartbeat
/projects/[projectId]/audit
/settings/accounts
/settings/system
```

Panel session is established on the API host (`/panel/login`). The dashboard calls `/api/panel/v1/me` with `credentials: "include"`. When frontend and API run on different hosts (e.g. `:3000` vs `:8080`), CSRF cookies are origin-scoped; use CORS with credentials or a same-origin reverse proxy in production.

### 17.3 UI Principles

- Operational dashboard, not marketing site.
- Dense, clear, quiet UI.
- Fast scanning over decorative layout.
- Tables for projects, keys, users, permissions, roles, and audit.
- Confirmation dialogs for destructive or sensitive actions.
- Clear disabled states when a module is inactive.
- Show API key secret once after creation.
- Never show stored API key secrets again.

## 18. Java SDK

### 18.1 Artifact

The starter lives at `packages/nexus-spring-boot-starter/` (root Gradle module,
`group = com.unzor`). In the monorepo, apps depend on it directly:

```groovy
implementation project(':packages:nexus-spring-boot-starter')
```

When published (future), the coordinate is `com.unzor:nexus-spring-boot-starter`
(currently `0.0.1-SNAPSHOT`). The example app `examples/spring-client-app` consumes it.

### 18.2 Configuration

```yaml
nexus:
  url: https://nexus.unzor.xyz
  api-key: ${NEXUS_API_KEY}
  app-name: F-Shop API
  instance-id: fshop-api-main-01
  heartbeat:
    enabled: true
    interval: 30s
  permissions:
    snapshot-ttl: 30s
    fail-closed: true
    declarations:
      - key: orders.read
        label: Ver pedidos
      - key: orders.cancel
        label: Cancelar pedidos
```

### 18.3 SDK Responsibilities

The starter (`nexus-spring-boot-starter`, **implemented**) autoconfigures two halves from `nexus.*`:

**Management** (active on `nexus.url`):

- auto-configure a `NexusClient`,
- send heartbeat,
- declare permissions from YAML + code (`PermissionDeclarationProvider`) providers,
- fetch and cache permission snapshots (`GET /api/v1/authz/users/{userId}/snapshot`),
- resolve wildcard permissions locally,
- deny on expired cache when Nexus is unavailable (fail-closed, configurable),
- read project **config** values (`nexus.config()`, `GET /api/v1/config/values[/{key}]`, scope `config:read`),
- read **vault** secrets (`nexus.vault()`, `GET /api/v1/vault/secrets[/{key}]`, scope `vault:read`),
- push **metrics** points (`nexus.metrics()`, `POST /api/v1/metrics/record`, scope `metrics:write`),
- expose typed clients,
- avoid hiding security failures.

The metrics module is also **Prometheus-compatible** on the backend side:
`GET /api/v1/metrics/export` (scope `metrics:read`) returns the project's recorded
metrics in Prometheus exposition format, so a Prometheus server can scrape them.
Prometheus `scrape_config` can't send `X-Nexus-Api-Key`, so the project API also
accepts the API key as `Authorization: Bearer <key>` (scrape with `bearer_token`).

```yaml
scrape_configs:
  - job_name: nexus-<project>
    bearer_token: <api-key with metrics:read>
    metrics_path: /api/v1/metrics/export
```

**Security** (active on `nexus.security.issuer`):

- resource-server chain validating Nexus JWTs locally (or introspection mode,
  `nexus.security.rs-mode=introspect`),
- OIDC client login (authorization-code + PKCE) + RP-initiated logout,
- `@perm` SpEL bean for permission-key authorization from the token claim,
- back-channel logout endpoint (RFC 8417) that validates the logout token and
  publishes a `NexusBackChannelLogoutEvent` for the app to invalidate the session.

### 18.4 SDK Client Shape

```java
@Service
public class OrderAuthorizationService {

    private final NexusClient nexus;

    public OrderAuthorizationService(NexusClient nexus) {
        this.nexus = nexus;
    }

    public boolean canCancelOrder(String userId) {
        return nexus.permissions()
                .can(userId, "orders.cancel");
    }

    public void notifyUploadCompleted() {
        nexus.notifications()
                .send("Archivo subido correctamente");
    }
}
```

### 18.5 Future SDKs

All SDKs must be generated or guided by OpenAPI contracts.

Future SDK targets:

- JavaScript/TypeScript,
- Python,
- Go,
- Kotlin.

The Java SDK must not become the only source of truth.

## 19. OpenAPI Requirement

Nexus must publish a complete OpenAPI document for:

- admin API,
- project API,
- error shapes,
- authentication headers,
- module endpoints.

Recommended endpoints:

```text
GET /api/docs/openapi.json
GET /api/docs
```

OpenAPI enables:

- SDK generation,
- integration tests,
- future open source adoption,
- clearer contracts between Nexus and apps.

## 20. Deployment

### 20.1 Docker Compose Shape

Recommended services:

```text
nexus-api
nexus-web
postgres
reverse-proxy
```

Alternative for simplicity:

```text
nexus-api
postgres
```

with Next.js dashboard hosted separately or built as static assets if the chosen mode allows it.

### 20.2 Required Environment Variables

These are the variables the API actually consumes (see `apps/api/src/main/resources/application*.properties`; `.env.example` documents each). Secrets marked ⚠ must be overridden from the dev default outside dev profiles (fail-closed otherwise).

```text
# Database / Redis
NEXUS_DATASOURCE_URL            # jdbc:postgresql://...
NEXUS_DATASOURCE_USERNAME
NEXUS_DATASOURCE_PASSWORD
NEXUS_REDIS_URL                 # redis://...

# OAuth / OIDC signing keys (PKCS12 keystore; ⚠ override dev keystore in prod)
NEXUS_OAUTH_JWK_KEYSTORE_LOCATION
NEXUS_OAUTH_JWK_KEYSTORE_PASSWORD
NEXUS_OAUTH_JWK_KEY_ALIAS
NEXUS_OAUTH_JWK_KEY_PASSWORD
NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET   # technical bootstrap client

# Vault (⚠ override dev master key in prod)
NEXUS_VAULT_MASTER_KEY          # derives AES key for TOTP secret + vault encryption

# Frontend / external API base (OAuth redirects, email links)
NEXUS_API_EXTERNAL_BASE_URL     # absolute base of the API as reachable externally
NEXUS_FRONTEND_BASE_URL         # Next.js dashboard base

# Spring Session (Redis-backed, shared panel + end-user)
NEXUS_SESSION_TIMEOUT
NEXUS_SESSION_COOKIE_NAME / _PATH / _HTTP_ONLY / _SAME_SITE / _SECURE / _MAX_AGE
NEXUS_SESSION_REVOCATION_RESUBMIT_INTERVAL / _BATCH_SIZE / _MAX_IN_FLIGHT / _MIN_AGE

# SMTP (transactional email: verify/reset/MFA notices, offline-notify)
NEXUS_SMTP_HOST / _PORT / _USERNAME / _PASSWORD / _FROM ...
```

The first registered account becomes `instanceAdmin` automatically (open registration bootstrap); there are no `NEXUS_ADMIN_BOOTSTRAP_*` credentials. JWT signing keys come from the PKCS12 keystore (not a `*_KEY_PATH`), API-key hashing uses the vault master key (not a separate pepper), and CSRF uses the standard `XSRF-TOKEN` double-submit cookie (no `NEXUS_COOKIE_SECRET`).

### 20.3 Operational Requirements

MVP should include:

- health endpoint,
- readiness endpoint,
- structured logs,
- database migrations on startup or deploy,
- Docker restart policy,
- backup instructions for PostgreSQL,
- bootstrap Nexus account and instance administrator flow.

The account registration endpoint is intentionally public. On an uninitialized
instance, the first successfully created Nexus account receives the single
`instanceAdmin` privilege. Operators must claim a new instance before exposing it
generally; concurrent registrations are serialized so only one account receives
the grant.

## 21. Security Requirements

### 21.1 API Keys

- Generate high-entropy keys.
- Store only hashes.
- Display full secret once.
- Support rotation.
- Support disable/revoke.
- Track last used time.
- Audit all key management operations.

### 21.2 Passwords

- Use Argon2id or bcrypt with strong parameters.
- Never log passwords.
- Rate limit login attempts.
- Audit failed login attempts without leaking sensitive data.

### 21.3 Tokens

- Use asymmetric signing keys for JWTs.
- Expose public keys through JWKS.
- Support key rotation in future.
- Keep access tokens short-lived.
- Store refresh tokens securely.

### 21.4 Admin Dashboard

- HTTP-only secure cookies for panel session.
- CSRF protection on panel and admin API mutations (`XSRF-TOKEN` / `X-XSRF-TOKEN`).
- Validate `continue` redirect targets against `nexus.frontend-base-url` to prevent open redirects.
- SameSite cookie settings; note cross-host limitations when Next.js and API differ by origin.
- Session revocation via `POST /api/panel/v1/session/logout`.
- Audit sensitive changes.

### 21.5 Authorization

- Default deny.
- Project isolation enforced at repository/query level and service level.
- API key project identity must override any request-provided project ID.
- Avoid "admin by naming convention"; admin access must be explicit.

## 22. Audit Strategy

Audit is a core capability, not an optional nice-to-have.

Audit events should be:

- append-only,
- queryable by project,
- queryable by actor,
- queryable by action,
- visible in dashboard,
- retained long enough for operational debugging.

Audit lives in its own Modulith module:

```text
audit/
├── api/
├── application/
├── domain/
└── persistence/
```

Primary responsibilities:

- receive audit commands from other modules,
- subscribe to important application events,
- normalize actor, resource, outcome, and trace metadata,
- persist append-only audit events,
- expose admin dashboard queries.

Recommended audit command shape:

```text
RecordAuditEventCommand
- project_id nullable
- trace_id nullable
- actor
- action
- resource
- outcome
- metadata
```

Audit should not block critical flows if the audit write fails due to transient infrastructure issues, but failures must be logged and observable. For sensitive security actions, prefer transactional audit writes where possible.

Every audit event created during an HTTP request should include the request `traceId`.

## 22.1 ADRs

Architectural decisions must be captured as ADRs under `docs/adr`.

ADRs should document decisions, not obvious implementation details. Good ADR topics:

- Nexus as source of truth,
- modular monolith with Spring Modulith,
- positive-only permissions in MVP,
- API keys identifying projects,
- PostgreSQL as primary database,
- trace IDs for request correlation.

## 23. Roadmap

### Phase 0: Bootstrap

Goal: create a runnable foundation.

Deliverables:

- Spring Boot project.
- Gradle wrapper.
- PostgreSQL integration.
- Flyway migrations.
- Testcontainers if desired.
- Health endpoint.
- Basic error format.
- Docker Compose for API and database.
- `./gradlew build` passes.

### Phase 1: Nexus Accounts and Projects

Goal: create the control plane base.

Deliverables:

- Nexus account model.
- Bootstrap Nexus account with `instanceAdmin = true`.
- Nexus account login/session.
- Instance administrator grants.
- Project CRUD.
- Project memberships and ownership roles.
- Project module table.
- Audit event table.
- Next.js dashboard scaffold.
- Project list/detail views.

### Phase 2: API Keys and Registry

Goal: allow apps to identify themselves.

Deliverables:

- Multiple API keys per project.
- API key generation and one-time display.
- API key validation filter.
- API key scopes.
- Heartbeat endpoint.
- Project heartbeat dashboard.
- Offline detection.

### Phase 3: Permissions

Goal: centralize authorization flags.

Deliverables:

- Permission catalog.
- Manual permission management in dashboard.
- YAML declaration via SDK/API.
- Code declaration via SDK.
- Roles.
- User role assignment.
- Direct user permission assignment.
- Permission check endpoint.
- Permission snapshot endpoint.
- Wildcard resolver.
- Positive-only permission tests.

### Phase 4: Identity

Goal: project-isolated auth.

Deliverables:

- Project users.
- OAuth clients per project.
- Spring Authorization Server integration.
- Project-specific issuer.
- Authorization Code + PKCE.
- JWT signing.
- JWKS endpoint.
- Refresh tokens.
- Session visibility/revocation.
- Auth audit events.
- Documentation of Spring Authorization Server internals.

### Phase 5: Java SDK

Goal: make Java app integration easy.

Deliverables:

- `nexus-spring-boot-starter`.
- Auto-configuration.
- API key configuration.
- Heartbeat client.
- Permission declaration client.
- Permission snapshot cache.
- Wildcard permission resolver.
- Fail-closed behavior.
- Integration sample app.

### Phase 6: Notify

Goal: centralize notifications.

Deliverables:

- Notify module toggle.
- Email channel (SMTP).
- Message send endpoint.
- Notification history.
- Notification audit.
- SDK client.

### Phase 7: Future Shared Services

Candidate modules:

- Storage,
- Secrets Vault,
- Config,
- Metrics/Uptime,
- Backup/Snapshot,
- Document Generator,
- Agent Hub,
- Runbook Engine,
- RAG.

## 24. Future Features

### 24.1 Environments

Future model:

```text
Project
└── Environments
    ├── dev
    ├── staging
    └── prod
```

This affects:

- API keys,
- users,
- OAuth clients,
- module config,
- permission assignments,
- secrets,
- storage.

Do not add this until there is a real need.

### 24.2 Negative Permissions

Future syntax:

```text
-orders.cancel
```

Needs:

- deterministic precedence,
- UI explanation,
- test matrix,
- audit clarity.

### 24.3 Permission Inheritance

Future role inheritance:

```text
admin inherits manager
manager inherits viewer
```

This should wait until simple roles are proven insufficient.

### 24.4 External Identity Federation

Future options:

- Google login,
- GitHub login,
- SAML,
- LDAP,
- passkeys.

### 24.5 Open Source Readiness

Before open sourcing:

- remove personal assumptions,
- document installation,
- document threat model,
- add example apps,
- add contribution guide,
- add license,
- add database migration policy,
- add security reporting policy.

## 25. Quality Gate

Required backend quality gate:

```bash
./gradlew build
```

The build should include:

- compilation,
- unit tests,
- integration tests included in Gradle lifecycle where feasible,
- static checks configured in Gradle if adopted.

Frontend quality gates should be defined when the Next.js package is scaffolded. Recommended future commands:

```bash
npm run lint
npm run build
```

## 26. Key Implementation Rules

- API contracts come before SDK convenience.
- Nexus APIs must be versioned.
- Project isolation must be enforced everywhere.
- Default authorization result is deny.
- API keys identify projects.
- Multiple API keys per project are first-class.
- API key scopes must be checked for project API endpoints.
- Permission sync must never delete assignments.
- Permission snapshots must expire.
- Expired snapshots fail closed.
- OAuth tokens must not cross project boundaries.
- Nexus accounts are separate from project users.
- Instance administration is a grant on a Nexus account.
- Project administration is expressed through project membership.
- Cross-module persistence uses IDs, not JPA entity associations.
- Audit security-sensitive operations.
- Do not add microservices before the modular monolith is under real pressure.

## 27. MVP Cut Line

The minimum useful Nexus should include:

- Nexus account login,
- projects,
- multiple API keys per project,
- module activation,
- heartbeat,
- isolated project users,
- permission catalog,
- roles,
- positive permission assignment,
- permission check,
- permission snapshot,
- basic audit,
- Java SDK with heartbeat and permissions.

Notify can follow immediately after MVP because it provides high personal value, but it does not need to block the permission/auth core.
