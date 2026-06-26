# ADR-0011: Persistent JWT signing keys

Status: Accepted

## Context

The OAuth2/OIDC Authorization Server signs access tokens and ID tokens with an RSA
key. Until now, `SecurityConfig.jwkSource()` generated a brand-new `RSAKey` with a
random `keyID` on **every application startup**. That had two consequences:

- **All outstanding tokens were invalidated on every restart/deploy.** Any client
  holding an access token had to re-authenticate (or use its refresh token) because
  the signing key no longer existed.
- **Horizontal scaling was impossible.** Two or more API instances would each
  generate their own key; a token issued by instance A would not validate on
  instance B, which validates JWTs against its own (different) key.

A common — but incorrect — justification for the in-memory approach was "to
invalidate old sessions." That is not the role of the signing key: invalidating
sessions/tokens selectively is handled by short access-token TTLs, refresh-token
rotation, and the revocable Redis-backed sessions described in ADR-0008. Rotating
the signing key on every boot is a blunt instrument with a heavy blast radius.

Refresh tokens already survive restarts because they are persisted in PostgreSQL
(the `oauth2_authorization` table, migration V4), so a well-implemented OAuth
client can recover by minting a new access token after a restart. But access tokens
in flight were still lost, and multi-instance remained broken.

## Decision

Load the signing key from a **configurable PKCS12 keystore** instead of generating
it in memory.

- Configuration namespace `nexus.oauth.jwk.*` (`keystore-location`, `keystore-password`,
  `key-alias`, `key-password`), overridable via environment. Absolute paths without a
  resource prefix (e.g. `/etc/nexus/keystore.p12`) are normalized to `file:` so they
  are not resolved as classpath resources.
- **A committed development keystore** (`src/main/resources/keystore/dev-jwk.p12`)
  ships with the app and is the default for the four properties. This makes local
  development and tests use **persistent, prod-like signing keys** (tokens survive
  restarts; readiness stays UP), consistent with the repo's convention of dev-default
  secrets overridable by env (e.g. the dev DB password `nexus`). **Production MUST
  override all four properties** to its own keystore; reusing the committed dev key in
  production is insecure.
- `jwkSource()` loads only the entry matching `key-alias` (the active signing key) and
  exposes a **single-key** `JWKSet`. See "Key rotation" for why overlap is not supported.
- If `keystore-location` is explicitly blank, the app falls back to an ephemeral
  in-memory key **and logs a warning**, and records the ephemeral state in
  `NexusOAuthJwkState`. The `JwkSigningKeyHealthIndicator` then reports the readiness
  health group `DOWN`, so a misconfigured production/multi-instance deployment is
  visible and does not receive traffic. This is a safety net only; the default config
  points at the dev keystore.
- The `keyID` (`kid`) is **stable and deterministic**, derived from the public key
  (not random), so the JWKS endpoint and the `kid` header of issued tokens stay
  consistent for the lifetime of a key.

Invalidating "old sessions" continues to be the responsibility of TTLs, refresh
rotation, and session revocation — not the signing key.

## Key rotation

The `JWKSet` exposes a **single** signing key, so rotation is a deliberate,
operator-driven operation:

1. Provision a new keystore whose active alias is the new key (or repoint
   `nexus.oauth.jwk.key-alias` to a new alias within the same keystore).
2. Redeploy. New tokens are signed with the new key. Outstanding access tokens
   signed with the previous key stop validating until affected clients use their
   (persisted) refresh token to mint a new one.
3. Because refresh tokens survive in PostgreSQL (V4), disruption is limited to
   access tokens already in flight.

**Graceful overlap** (serving both the old and the new key simultaneously so
outstanding access tokens keep validating across a rotation, with no client
disruption) is **out of scope by decision**. Rotation invalidating in-flight access
tokens is acceptable: clients recover via their (persisted) refresh token, and there
is no zero-downtime rotation requirement. A multi-key attempt was also reverted
because Spring's default `NimbusJwtEncoder` refuses to sign when more than one key is
present (`Failed to select a key since there are multiple for the signing
algorithm`), so overlap would additionally require a custom `JwkSelector`/encoder —
but the decision not to pursue it stands regardless. See
`docs/deployment/jwt-signing-keys.md` for the production key generation and rotation
runbook.

The single-key load + sign + validate path is covered by `SecurityConfigJwkTests`.

## Consequences

- A restart no longer mass-invalidates access tokens: dev/tests use the committed
  keystore, production uses the operator-provided one.
- Multiple instances validate each other's tokens as long as they share the keystore.
- Rotation is **not** graceful: outstanding access tokens are invalidated until
  clients refresh. This is **accepted by decision**; overlap is out of scope.
- Operators are responsible for key management: provisioning the production keystore,
  keeping the private keys out of version control, and rotating deliberately.
- If no keystore is configured (location blank), readiness is `DOWN` (ephemeral keys).
  With the default config this does not happen; it only surfaces a deliberate or
  misconfigured blanking of the location.
- This decision supersedes the implicit "ephemeral keys" behavior; the dev fallback
  is retained intentionally as a safety net.
