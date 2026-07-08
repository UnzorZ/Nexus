# ADR-0016: Per-Project Multi-Issuer OAuth via Path-Component Issuers

Status: Accepted

## Context

The spec (§15.3) requires **each project to be its own OAuth/OIDC issuer**
(`https://{host}/p/{projectSlug}`), with Auth Code + PKCE, project-scoped OAuth
clients (§9.6), and access/ID tokens carrying `project_id` + `authz_version`
claims that never cross project boundaries. Before B2 the AS was a single global
issuer at `/oauth2/**` with one technical bootstrap client — no per-project
issuers, no project clients, and no project claims on tokens.

The identity module already ships Spring Authorization Server (SAS) 7.0.5 on a
single PostgreSQL schema (SAS-default `oauth2_*` tables, no `project_id`), a
single shared RSA signing key (ADR-0011, deterministic `kid`), and a global
technical bootstrap client reconciled at startup. B1 added `ProjectUser` CRUD +
a session login under `/p/**`.

## Decision

1. **Path-component issuers via SAS multitenancy.** Flip
   `AuthorizationServerSettings.multipleIssuersAllowed(true)`. SAS then resolves
   the issuer **per request** from the URL path
   (`/p/{slug}/oauth2/authorize` ⇒ issuer `{host}/p/{slug}`) and self-widens the
   AS chain's `endpointsMatcher` to `/**/oauth2/**` + `/**/.well-known/**`, so
   the existing `securityMatcher` needs no edit and `@Order(1)` still wins over
   the `/p/**` `@Order(4)` chain. The global `/oauth2/**` surface (bootstrap
   client) keeps working.

2. **Minimal delegation — one project-aware component.** Keep `JWKSource`
   **single/shared** (ADR-0011) and the `OAuth2AuthorizationService` /
   `OAuth2AuthorizationConsentService` as the stock JDBC impls (they key by
   `registered_client_id` + `principal_name`; project clients have globally-unique
   ids, so isolation holds). Make **only** `RegisteredClientRepository`
   project-aware: a `CompositeRegisteredClientRepository` that resolves a client
   from `project_oauth_clients` first and falls back to the global
   `JdbcRegisteredClientRepository` (the bootstrap client). Its `save` delegates
   to the global repo (only the bootstrap runner calls it).

3. **Shared signing keys, not per-project keys.** ADR-0011 already chose a single
   active key (deterministic `kid`, no graceful overlap). We keep that: **one key
   signs every project's tokens.** Project isolation is enforced by the `iss`
   claim (per-project) + the mandatory `project_id` claim + resource-server
   validation, **not** by separate keys. This supersedes the identity roadmap's
   "claves por proyecto" item — per-project keystores are a large future
   investment with no isolation benefit over claim-based isolation on a single
   Nexus deployment.

4. **Project claims via `OAuth2TokenCustomizer`.** A `ProjectIdTokenCustomizer`
   reads the resolved issuer, resolves the project from the slug, and (when the
   principal is a `ProjectUserPrincipal`) adds `project_id`, `authz_version` and
   `permissions` (effective permission keys, wildcards verbatim, ADR-0003) to
   access/ID tokens; `/userinfo` exposes the same claims via a `userInfoMapper`.
   `iss` is set by the framework.

5. **Token lifetimes.** Access tokens 10 min, refresh tokens 7 d rolling
   (`TokenSettings.reuseRefreshTokens(true)` rotates the refresh token on each
   use). The spec mandates only "short-lived access tokens"; these are the chosen
   defaults.

6. **Login redirect per project.** An unauthenticated HTML request to
   `/p/{slug}/oauth2/authorize` hits a `ProjectOauthAuthenticationEntryPoint`
   that 302s to `/p/{slug}/login?continue=...` (B1's login), instead of the
   global login. The ProjectUser session established under `/p/**` is visible to
   the AS chain (shared Spring Session + `SecurityContext`).

## Consequences

- **Projects get real OAuth at `/p/{slug}`.** Discovery, JWKS, authorize, token,
  userinfo are served per project; tokens carry `iss`=`{host}/p/{slug}`,
  `project_id`, `authz_version` and `permissions`. A reference consumer
  (`examples/spring-client-app`) demonstrates local-JWT validation and
  permission glob-matching.
- **OAuth clients are panel-managed.** `project_oauth_clients` (V27) holds
  project-scoped clients; secrets are `{bcrypt}`-hashed and shown once; public
  clients force PKCE.
- **Bootstrap client unchanged.** The global technical client still reconciles
  into `oauth2_registered_client`; the composite routes it via the global fallback.
- **One migration added, versioned V27** (above the original master V1–V11). The
  `feat/greenfield-modules` and `feat/instance-smtp` migrations were later renumbered
  from their branch versions (V12–V26) to **V29–V43** — above this V27/V28 — so they
  apply cleanly on deployments that had already shipped V27/V28 (Flyway with
  out-of-order disabled would otherwise skip versions below the installed max).
- **`authz_version` is read-only for now.** The claim mirrors the ProjectUser's
  current value (starts at 0); the bump wiring (`incrementAuthzVersion()` on
  role/permission assignment) lands with the permissions→user binding (B3).

## Deferred

Per-project signing keys; consent screen UI (first-party clients auto-approve);
RP-Initiated Logout UI; a `project_id` column on `oauth2_authorization`
(defense-in-depth); JWK rotation tooling.

> **No longer deferred:** token introspection (implemented via
> `AuthzVersionIntrospectionAuthenticationProvider`, enforces `authz_version`),
> `authz_version` bump wiring (`incrementAuthzVersion()` on role/permission
> change), and the `permissions` claim + reference resource-server app (M7).
