# Nexus — Spring Boot 4 reference client + resource-server app

A self-contained reference application that integrates with a **Nexus** project
as both an **OIDC client** (logs users in via Nexus) **and a resource server**
(protects its own `/api/**` with Nexus-issued JWTs). It demonstrates every
OAuth/OIDC capability a Nexus consumer needs, with clean, lift-and-reuse code.

> This is the canonical "how do I integrate with Nexus?" example. It uses the
> standard Spring Security OAuth2 client + resource-server stack — no proprietary
> SDK required.

## What it demonstrates

| Capability | Where |
|---|---|
| OIDC login (authorization-code + PKCE) | `SecurityConfig` (`oauth2Login`) |
| Local JWT validation (signature + `exp`) | `ResourceServerSecurity.jwtApiFilterChain` |
| **Permission-key authorization** from the `permissions` claim (glob-match) | `PermissionService` + `PermissionMatcher`, `@PreAuthorize("@perm.has(...)")` |
| Scope → authority mapping | `NexusJwtAuthenticationConverter` |
| Refresh-token flow (silent renewal) | `RefreshController` (`@RegisteredOAuth2AuthorizedClient`) |
| `/userinfo` + ID-token claim inspection | `HomeController`, `MeApiController` |
| `authz_version` revocation via introspection | `ResourceServerSecurity.introspectApiFilterChain` (`NEXUS_RS_MODE=introspect`) |
| RP-initiated logout | `SecurityConfig` (`OidcClientInitiatedLogoutSuccessHandler`) |

## How authorization works

Nexus puts the user's effective permission keys in the `permissions` claim of the
access token (and ID token + `/userinfo`), **verbatim, including wildcards**
(`orders.*`, `*`) — per ADR-0003 and the Nexus spec §14.3. A resource server
matches them with three rules:

- **exact** — `orders.read` covers `orders.read`
- **namespace wildcard** — `orders.*` covers `orders.read` (and any key under the
  `orders.` prefix, e.g. `orders.billing.export`)
- **global wildcard** — `*` covers everything

`PermissionMatcher` implements this in ~15 lines. `PermissionService` is a SpEL
bean (`@perm`) that reads the claim off the JWT, so controllers authorize with:

```java
@GetMapping("/api/orders")
@PreAuthorize("@perm.has(authentication, 'orders.read')")
public List<OrderSummary> listOrders() { ... }
```

A token carrying `permissions:["orders.*"]` (or `["*"]`) satisfies it.

## Prerequisites

1. **Nexus running** on `http://localhost:8080` (`./gradlew :apps:nexus-api:bootRun`
   with the dev compose `postgres` + `redis` up).
2. A **project** with slug, e.g. `f-shop`.
3. An **OAuth client** for that project, created in the Nexus panel, with:
   - redirect URI: `http://localhost:8081/login/oauth2/code/nexus`
   - grant type: `authorization_code`
   - scopes: `openid profile`
4. A **role** in that project granting the demo permissions — at least
   `orders.*` and `inventory.read` — assigned to a `ProjectUser`.
   (See the Nexus panel: *Projects → Roles* and *Members*.)

## Configure

Set the project slug and OAuth client credentials (defaults: slug `f-shop`):

```bash
export NEXUS_PROJECT_SLUG=f-shop
export NEXUS_CLIENT_ID=<your-client-id>
export NEXUS_CLIENT_SECRET=<your-client-secret>
```

## Run

```bash
cd examples/spring-client-app
./gradlew bootRun          # serves on http://localhost:8081
```

> The example is a **standalone Gradle project** (not part of the root build).

## Walkthrough

1. Open `http://localhost:8081/` → redirected to Nexus to log in (your
   `ProjectUser`).
2. After consent you land on the home page, which shows your **ID-token claims** —
   including `permissions: ["orders.*", ...]`, `project_id`, `authz_version`,
   `amr` — and your `/userinfo` claims.
3. Click **GET /api/orders** → `200` with the demo orders (your `orders.*`
   permission satisfies `orders.read` via glob-match). Click **GET /api/me** to
   see the raw JWT claims the resource server decoded locally.
4. **Refresh token**: visit `/refresh` — it shows the current access token + its
   expiry and the presence of a refresh token. Reload after the token expires to
   see a freshly minted one (the SDK renewed it transparently).
5. **Revocation story**:
   - Default (`NEXUS_RS_MODE=jwt`, local validation): revoke the role in Nexus,
     then call `/api/orders` again — the token is still honored until its `exp`
     (access tokens are short-lived, 10 min — see the
     [threat model](../../docs/threat-model.md)). The `permissions` snapshot is
     baked into the JWT.
   - Introspection (`NEXUS_RS_MODE=introspect`): each request is validated against
     `/oauth2/introspect`, which returns `active:false` when `authz_version` is
     stale — so a revoked role takes effect immediately (at the cost of a
     server round-trip per request). Enable the opaque-token config in
     `application.yml` and set the mode.
6. **Log out** → RP-initiated logout ends both the local session and the Nexus
   session (via the issuer's `end_session_endpoint`).

## curl alternative (no browser)

```bash
# Mint a token at the project's token endpoint (authorization-code needs a code;
# for a scripted check use the token your client obtained, or client-credentials
# for machine-to-machine — note: machine tokens carry no `permissions` claim).
TOKEN=...
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/orders
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/me
```

Decode the JWT at https://jwt.io to eyeball the `permissions` claim in the
access and ID tokens.

## Tests

```bash
./gradlew build
```

- `PermissionMatcherTest` — the §14.3 glob rules (exact / namespace / global).
- `OrdersApiAuthorizationTest` — end-to-end: `permissions:["orders.*"]` → `200`,
  empty/missing → `403`, driven with a mocked JWT.

## Further reading

- Nexus spec §14.3 (permission matching) and §15.3 (token claims) —
  `docs/nexus-technical-spec.md`
- Per-project multi-issuer design — `docs/adr/0016-per-project-multi-issuer-oauth.md`
- JWT signing keys / rotation — `docs/adr/0011-persistent-jwt-signing-keys.md`,
  `docs/deployment/jwt-signing-keys.md`
- Open risk: local-JWT validation vs. revocation — `docs/threat-model.md`
