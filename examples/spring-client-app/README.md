# Nexus — Spring Boot 4 reference client app (uses `nexus-spring-boot-starter`)

A self-contained reference application that integrates with a **Nexus** project as
both an **OIDC client** (logs users in via Nexus) **and a resource server**
(protects its own `/api/**` with Nexus-issued JWTs) **and a managed instance**
(heartbeats, declares its permissions, caches permission snapshots, receives
back-channel logout). It consumes the [`nexus-spring-boot-starter`](../../packages/nexus-spring-boot-starter)
— one dependency — instead of hand-wiring Spring Security.

> The app itself is ~6 controllers + a couple of beans; **all** the integration
> (security chains, JWT validation, `@perm`, heartbeat, snapshot cache, declaration
> sync, back-channel logout endpoint) is autoconfigured by the starter from the
> `nexus.*` properties in `application.yml`.

## What it demonstrates

| Capability | Where (in the app) | Provided by |
|---|---|---|
| OIDC login (authorization-code + PKCE) + RP-initiated logout | — | starter `NexusSecurityAutoConfiguration` |
| Local JWT validation (signature + `exp` + **`iss`**) | `/api/**` | starter resource-server chain |
| **Permission-key authz** from the `permissions` claim (glob) | `OrdersApiController`, `InventoryApiController` (`@perm.has`) | starter `NexusPermissionService` |
| **Permission snapshot** (fresh/authoritative, cached TTL) | `NexusDemoController` (`/admin/snapshot`) | starter `PermissionSnapshotCache` + `NexusClient` |
| **Heartbeat** to Nexus | automatic on startup | starter `HeartbeatScheduler` |
| **Permission declaration** (YAML + code) | `application.yml` + `CodePermissionDeclarations` | starter `PermissionDeclarationSync` |
| **Notify** via Nexus | `NexusDemoController` (`/admin/notify`) | starter `NotifyClient` |
| **Project config** (read values) | `NexusDemoController` (`/admin/config`) | starter `ConfigClient` |
| **Vault** (reveal secrets) | `NexusDemoController` (`/admin/vault`) | starter `VaultClient` |
| **Metrics push** (record points) | `NexusDemoController` (`/admin/metrics`) | starter `MetricsClient` |
| **Back-channel logout** (RFC 8417) | `BackChannelLogoutListener` | starter `NexusBackChannelLogoutController` |
| `authz_version` revocation via introspection | `NEXUS_RS_MODE=introspect` | starter resource-server chain |
| Refresh-token flow (silent renewal) | `RefreshController` | Spring Security OAuth2 client |

> **Cross-realm isolation.** Every project realm shares one signing key, so the
> resource-server chain validates the token `iss` against the configured Nexus
> issuer — this is what stops a valid token issued by project A from
> authenticating against an app configured for project B.

## How authorization works

Nexus puts the user's effective permission keys in the `permissions` claim of the
access token (and ID token + `/userinfo`), **verbatim, including wildcards**
(`orders.*`, `*`) — per ADR-0003 and the Nexus spec §14.3. Two complementary
paths in this app:

- **Token-claim (optimistic)** — `@PreAuthorize("@perm.has(authentication, 'orders.read')")`
  on the resource-server endpoints. Matches with three rules:
  - **exact** — `orders.read` covers `orders.read`
  - **namespace wildcard** — `orders.*` covers `orders.read` (and any key under
    `orders.`, e.g. `orders.billing.export`), but **not** bare `orders`
  - **global wildcard** — `*` covers everything
- **Snapshot (fresh/authoritative)** — `nexusClient.permissions().can(userId, key)`
  resolves from a cached permission snapshot (`GET /api/v1/authz/users/{id}/snapshot`)
  for decisions outside the request thread or when you need `authz_version` freshness.

Token-claim authz is honored until the token `exp`; a role revocation reaches the
snapshot path immediately (and the introspect mode enforces it per request).

## Prerequisites

1. **Nexus** running (default `http://localhost:8080`).
2. A **project** (slug, e.g. `f-shop`).
3. An **OAuth client** in that project:
   - redirect URI `http://localhost:8081/login/oauth2/code/nexus`
   - `backchannel_logout_uri` `http://localhost:8081/logout/backchannel`
   - grant `authorization_code` + `refresh_token`, scopes `openid profile`
4. An **API key** for the project with scopes:
   `registry:heartbeat`, `authz:snapshot`, `permissions:declare`, `notify:send`,
   `config:read`, `vault:read`, `metrics:write` (add `metrics:read` if you scrape
   the Prometheus export with this key).
5. A **role** granting the demo permissions (`orders.*`, `inventory.read`,
   `reports.export`) and a **project user** assigned that role.
6. (For the `/admin/config` and `/admin/vault` demos) a **config value**
   (e.g. key `demo.feature`) and a **vault secret** (e.g. key `demo-secret`)
   created in the project.

## Configure & run

Environment variables (see `application.yml`):

| Variable | Required | Example |
|---|---|---|
| `NEXUS_API_KEY` | yes | `nxs_fshop_…` |
| `NEXUS_CLIENT_ID` | yes | the OAuth client id |
| `NEXUS_CLIENT_SECRET` | yes | the OAuth client secret |
| `NEXUS_PROJECT_SLUG` | no (default `f-shop`) | `f-shop` |
| `NEXUS_URL` | no (default `http://localhost:8080`) | `https://nexus.example.com` |
| `NEXUS_RS_MODE` | no (default `jwt`) | `introspect` |

Run (the app is a root Gradle module):

```bash
./gradlew :examples:nexus-spring-client-app:bootRun
# → http://localhost:8081
```

## Walkthrough

1. Open `http://localhost:8081` → redirected to Nexus to sign in (OIDC).
2. After login, the **home** page shows the ID-token + `/userinfo` claims
   (`sub`, `project_id`, `authz_version`, `permissions`, `amr`).
3. `GET /api/orders` (with the bearer) → **200** (token carries `orders.*`).
4. `GET /api/me` → echoes the decoded access-token claims.
5. `/admin/snapshot?userId=<uuid>` → the cached permission snapshot for that user.
6. Sign out of Nexus → Nexus POSTs a back-channel logout token to
   `/logout/backchannel` → the app logs it (in a real app, it'd invalidate the
   session for that `sub`).
7. `/admin/config?key=demo.feature` → the config value via `nexus.config()`.
8. `/admin/vault?key=demo-secret` → the revealed secret via `nexus.vault()`.
9. `/admin/metrics` → record a point via `nexus.metrics()`, then scrape it (below).

## Prometheus

The metrics you push are also exposed by the backend in Prometheus exposition
format, so a Prometheus server can scrape them:

```bash
curl -H "Authorization: Bearer $NEXUS_API_KEY" \
     "$NEXUS_URL/api/v1/metrics/export"   # -> # TYPE orders_created gauge ...
```

```yaml
scrape_configs:
  - job_name: nexus-demo
    bearer_token: <api-key with metrics:read>   # Prometheus can't send X-Nexus-Api-Key
    metrics_path: /api/v1/metrics/export
    static_configs:
      - targets: ['localhost:8080']
```

(The API key is accepted as `Authorization: Bearer` anywhere in `/api/v1/**` —
handy for any bearer-only client, not just Prometheus.)

## Tests

`./gradlew :examples:nexus-spring-client-app:test` — `OrdersApiAuthorizationTest`
drives `GET /api/orders` with mocked JWTs (`orders.*` / `*` / `orders.read` → 200;
empty / missing / unrelated → 403).

## Further reading

- Starter: [`packages/nexus-spring-boot-starter`](../../packages/nexus-spring-boot-starter)
- Spec §14.3 (permission glob), §14.11 (snapshot), §18 (SDK)
- ADR-0003 (permission model), ADR-0016 (multi-issuer OAuth)
