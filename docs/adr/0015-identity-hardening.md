# ADR-0015: Identity login hardening + OAuth UX completion

Status: Accepted

## Context

B1 made the project-user login functional and B2 turned the AS into a per-project
multi-issuer server. Before identity can run in production, two classes of gap remain:

1. **The login path leaks information and has no brute-force protection.**
   `ProjectSessionAuthenticator` returned immediately on a non-existent user without
   running bcrypt, while an existing-user-wrong-password paid the full bcrypt cost — a
   timing oracle for username enumeration. There was no rate-limiting or account lockout.
2. **Dev secrets are accepted in every profile.** The OAuth bootstrap secret
   `changeme-local-dev` (`application.properties`) and the committed dev JWK keystore
   (`classpath:keystore/dev-jwk.p12`, ADR-0011) are used with no guard. ADR-0011 already
   states "reusing the dev key in production is insecure"; the security review flagged
   both as "deferred to pre-prod identity hardening".
3. **The OAuth UX is unbranded/incomplete:** SAS ships a default consent page and exposes
   RP-initiated logout + introspection by default, but there was no branded consent page,
   no SP-initiated logout, and introspection was unverified.

## Decision

1. **Anti-enumeration + timing equalization.** All login failures (unknown user,
   suspended, locked, wrong password) collapse to the same generic message. The
   unknown-user branch now runs a dummy bcrypt `matches` (hash precomputed at construction)
   so every failure path performs exactly one bcrypt comparison — eliminating the timing
   oracle without hardcoding a hash.

2. **Per-user, DB-backed account lockout.** `project_users` gains `failed_login_attempts`
   (int) + `locked_until` (timestamptz) via V28. After `nexus.identity.login.max-attempts`
   (default 5) consecutive failures the account is locked for
   `nexus.identity.login.lockout-duration` (default 15m). A locked account is rejected with
   the same generic error (no enumeration). The state lives in Postgres, so it is respected
   across instances and survives restarts. Lockout is checked *before* the password
   comparison; a successful login resets the counter. Per-IP lockout is deferred.

3. **Fail-closed dev secrets (`IdentityStartupGuard`).** An `ApplicationRunner` aborts
   startup with `IllegalStateException` if the dev bootstrap secret **or** the dev JWK
   keystore is in use under a non-dev profile. The allowlist is: the default (empty)
   profile, `dev`, `local`, `test`, `remote-dev`. Any other active profile (e.g. `prod`,
   `staging`) fails fast until operators set a real `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET`
   and a production keystore. This is forward-looking: the default profile stays permitted
   so dev and the existing test suite (which run on the default profile with the dev
   secrets) are unaffected.

4. **Branded consent page.** `authorizationEndpoint.consentPage("/oauth2/consent")` +
   `ConsentController` + `templates/identity/project-consent.html`. **Multi-issuer note:**
   SAS resolves the consent redirect as `{scheme}://{host}{consentPage}` and does **not**
   preserve the `/p/{slug}` segment, so the consent form must POST back to
   `/p/{slug}/oauth2/authorize` (not the global `/oauth2/authorize`) for the issuer to
   resolve to the correct project realm. `ConsentController` reconstructs the slug from the
   `client_id` (project client → `ProjectOauthClientRepository.findByClientId` →
   `ProjectLookupService.requireSlug`); the global bootstrap client posts to
   `/oauth2/authorize`. Only clients with `consent_required=true` trigger it (opt-in per
   client; the bootstrap client requires consent by default).

5. **SP-initiated logout.** `GET/POST /p/{slug}/logout` invalidates the session, clears the
   `SecurityContext`, and renders a branded signed-out page (`project-signed-out.html`).
   `/p/*/logout` is `permitAll` so the signed-out page is reachable without a session; the
   POST is still CSRF-protected.

6. **RP-initiated logout + introspection: rely on SAS defaults, verify by test.**
   `.oidc(withDefaults())` already exposes the OIDC `end_session_endpoint`
   (`/p/{slug}/connect/logout`) and the token introspection endpoint
   (`/p/{slug}/oauth2/introspect`, client Basic auth). Clients already carry
   `postLogoutRedirectUris` (bootstrap default + the project-client column). `MultiIssuerEndpointsIT`
   asserts the `end_session_endpoint` and `introspection_endpoint` in each realm's discovery.
   A custom branded `logoutResponseHandler` for RP-initiated logout is deferred.

## Consequences

- The login timing oracle is closed and brute-force is throttled per user, multi-instance.
- A future `prod`/`staging` profile refuses to start with the committed dev secrets — the
  misconfiguration can't silently ship.
- Project admins can opt a client into a branded consent flow; the consent POST preserves
  the project issuer under multi-issuer.
- Users can sign out of a project realm via a branded page; the OIDC end-session and
  introspection endpoints are verified present and functional.
- One migration added (**V28**), numbered above the V27 of B2.

## Deferred

Per-IP lockout; a custom branded `logoutResponseHandler` for RP-initiated logout; the
user↔role/permission assignment module (which would drive `authz_version` bumps); email-
verification + password-reset flows; per-project signing keys (ADR-0013 chose shared).
