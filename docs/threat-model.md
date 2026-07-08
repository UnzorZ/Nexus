# Nexus Threat Model

A lightweight, evolving threat model for self-hosted Nexus deployments. It is
intentionally practical (STRIDE-flavoured), not formal. Update it when significant
new attack surface lands (new auth flows, new endpoints, new secrets).

## Scope and Assets

What we are protecting:

| Asset | Where | Sensitivity |
|---|---|---|
| Project data (users, roles, API keys, audit, metrics, secrets) | PostgreSQL | High |
| Sessions + ephemeral security state | Redis | Medium-High |
| JWT signing keys (per-instance keystore) | Mounted keystore | Critical |
| Vault master key (AES-256-GCM) | Env var | Critical |
| OAuth bootstrap client secret | Env var / DB | High |
| OAuth access/refresh/id tokens | In transit + `oauth2_authorization` rows | High |
| User passwords | `project_users.password_hash` (bcrypt) | High |

## Trust Boundaries

```
 [Browser]  ──HTTPS──▶  [Next.js dashboard]  ──HTTPS(CORS+CSRF+credentials)──▶  [Spring API :8080]
                                                                              │
                          [OAuth Relying Party]  ──HTTPS──▶  /p/{slug}/oauth2/* │
                                                                              ├──▶ PostgreSQL
                                                                              └──▶ Redis
```

- The **browser** is fully untrusted. The dashboard is client-rendered and calls the
  API with credentials over a restricted CORS allowlist (never `*`).
- The **API** is the trust root. It enforces authn/authz, CSRF, session, and
  fail-closed defaults.
- **OAuth RPs** are semi-trusted third parties; they may validate JWTs locally or
  introspect tokens.
- **PostgreSQL / Redis** are trusted infrastructure assumed to be on a private
  network (compose binds Redis to loopback).

## Threats and Mitigations

| Threat (STRIDE) | How Nexus mitigates it | Where |
|---|---|---|
| **Credential stuffing / brute force** | Per-IP token-bucket rate limiting on public auth endpoints (login/MFA/token/register/verify/reset) evaluated before the security chain; plus per-user failed-login lockout and dummy-password bcrypt timing equalization | `RateLimitFilter`, `ProjectSessionAuthenticator`, `IdentityLoginProperties` |
| **Account enumeration (login)** | Every login failure collapses to one generic message + timing equalization | `ProjectSessionAuthenticator` |
| **Account enumeration (password reset / verify)** | Request endpoints always return success; tokens are hashed at rest; verify/reset only reveal state after the secret (token) is proven | Track A flows (`password-reset`, `verify-email`) |
| **Token theft / staleness after role change** | `authz_version` stamped on tokens; introspection returns `active:false` when stale or user deleted | `AuthzVersionIntrospectionAuthenticationProvider` |
| **Session fixation** | Session id rotated on successful authentication | `ProjectSessionAuthenticator` |
| **CSRF** | CSRF token on state-changing routes; cookies `SameSite` (Lax default, None under `remote-dev` over HTTPS) | `SecurityConfig`, `csrf.ts` |
| **Open redirect** | `continue`/post-login targets validated to the same realm | `safePostLoginTarget`, `isInternalPath` |
| **XSS in email/notification preview** | Sanitized only in-browser with DOMPurify; outbound email bodies are HTML-escaped for reported values | `InstanceOfflineNotifier.escape`, dashboard preview |
| **Secrets at rest** | API-key secrets hashed (peppered); MFA TOTP secrets encrypted (AES-256-GCM, reversible to compute codes), recovery codes hashed SHA-256 single-use; vault uses AES-256-GCM | `VaultCrypto`, `TotpCrypto`, `ProjectApiKey` |
| **Misconfigured production** | Fail-closed startup guard + VaultCrypto abort when dev keystore / dev master key are used outside dev profiles | `IdentityStartupGuard`, `VaultCrypto` |
| **Information disclosure via Actuator** | Only `health` (+ liveness/readiness) and `prometheus` are public; the rest require HTTP Basic | `SecurityConfiguration` |

## Open / Accepted Risks

- `/actuator/prometheus` is public by default to ease scraping from a trusted network.
  Public deployments should restrict it (reverse-proxy allowlist, separate
  `management.server.port`, or remove it from `permitAll` to restore HTTP Basic).
- Per-IP rate limiting covers public auth endpoints (login/MFA/token/register/verify/
  reset). Other endpoints (introspection, userinfo, the panel data API) are not limited
  at the app level — rely on the per-user lockout and a front proxy's connection
  limits there. Tune or disable via `nexus.ratelimit.*`.
- Locally-validated JWTs (resource servers that do not introspect) honor `authz_version`
  only until token `exp`. Since access tokens now carry the user's `permissions` claim
  verbatim (an optimistic snapshot baked into the JWT), a role revocation does **not**
  reach such a consumer until the token expires (access tokens are short-lived, 10 min).
  For revocation-sensitive endpoints, validate via `/oauth2/introspect` — it enforces
  `authz_version` and returns `active:false` for stale tokens — or call the permission
  snapshot/check API. The reference resource-server app
  ([`examples/spring-client-app`](../examples/spring-client-app)) demonstrates both modes
  (`NEXUS_RS_MODE=jwt` local validation vs `introspect`). A future resource-server +
  Redis denylist would cover per-request enforcement without per-request introspection.
- Backups: a reference `scripts/backup-db.sh` + runbook (`docs/deployment/backups.md`)
  are provided, but the operator is still responsible for scheduling them, testing
  restores, and keeping off-host copies. No managed backup is bundled.

## Operational Assumptions for Self-Hosters

- TLS terminates at the edge or on the API origin.
- PostgreSQL and Redis run on managed or network-isolated infrastructure.
- The JWT keystore and Vault master key are real secrets (the dev defaults are rejected
  under the `prod` profile).
- `compose.prod.yaml` fails fast until these are provided.
