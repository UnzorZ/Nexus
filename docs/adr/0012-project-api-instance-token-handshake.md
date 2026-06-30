# ADR-0012: Project API instance-token handshake for high-frequency endpoints

Status: Accepted

## Context

ADR-0004 established that **API keys identify a project** to Nexus, and the
project API (`/api/v1/**`) authenticates each request statelessly with the
`X-Nexus-Api-Key` header (spec §10.2). The `ApiKeyResolver` does, **per
request**:

1. prefix lookup on `project_api_keys` (indexed),
2. constant-time SHA-256 verification of the full key against `key_hash`
   (`MessageDigest.isEqual`),
3. status/expiry checks, and
4. an `UPDATE last_used_at` on the matched key.

That is fine for low-frequency, management-style project-API calls. But
**heartbeat** (spec §13.1) is high-frequency telemetry: an SDK reports every
~30s, from every instance. At even modest scale (N instances × every 30s), the
per-beat cost above — a SHA-256 over the full key **plus a DB write per beat** —
becomes the dominant server load, and the long-lived secret is transmitted on
**every beat**, widening the surface for proxy/APM logging or interception.

We considered three options:

- **Status quo** — raw API key on every `/api/v1/**` request (current).
- **HMAC request signing** — client signs each request; the secret never
  travels. Rejected: the server would have to store the secret in a reversible
  form to recompute the HMAC, which is **worse for at-rest security** than the
  current "hash only" model. More SDK complexity for no at-rest gain.
- **Instance-token handshake** — the SDK presents the long-lived key **once** to
  obtain a short-lived token, then sends only the token per beat.

## Decision

Introduce a **lightweight instance-token handshake** for high-frequency
project-API endpoints (heartbeat first), while keeping raw `X-Nexus-Api-Key` for
low-frequency / management-style project-API calls and as the bootstrap for the
handshake.

Flow:

1. `POST /api/v1/registry/register` with `X-Nexus-Api-Key` (scope
   `registry:heartbeat`). The resolver validates the key exactly as today
   (prefix + constant-time SHA-256 + status/expiry), then mints an **instance
   token** bound to `(projectId, keyId, instanceId, scopes)` with a short TTL
   (e.g. 1h).
2. The token is **opaque and Redis-backed** (the shared, revocable store from
   ADR-0008), so it is **individually revocable** (disabling the key, or an
   explicit revoke, kills outstanding tokens) and verified by a single Redis
   `GET` — **no SHA-256 over the full key and no `last_used_at` write per beat**.
3. `POST /api/v1/registry/heartbeat` authenticates with the instance token
   (`Authorization: Bearer` or `X-Nexus-Instance-Token`) instead of the
   long-lived key.
4. The SDK refreshes the token before expiry and re-registers on a `401`.

Raw `X-Nexus-Api-Key` remains valid on `/api/v1/**` (back-compat, and for
low-frequency calls), so the handshake is an **optimization, not a replacement**.

## Consequences

- **Server (Nexus): net win at scale.** Per-beat auth drops from (prefix lookup
  + constant-time SHA-256 + `last_used_at` write) to a Redis `GET`; the
  expensive key verification happens ~once per token TTL. For low-volume
  endpoints there is no benefit, so the handshake is opt-in per endpoint
  (heartbeat today).
- **SDK: more complex, not faster per beat.** Each beat is still one HTTPS POST
  (TLS reused via keep-alive); the handshake **adds** a register call and
  token-lifecycle logic (refresh-before-expiry, 401 → re-register retry). The
  win is server-side scalability + reduced frequency of long-lived-secret
  transmission, not client throughput.
- **Revocation:** because tokens are Redis-backed and bound to the key, disabling
  or rotating the API key invalidates outstanding instance tokens, consistent
  with ADR-0008's revocable-session model.
- **Backwards compatible:** existing `X-Nexus-Api-Key` callers keep working; the
  SDK adopts the handshake opportunistically.

## Hardening (follow-up)

Two gaps surfaced after the initial implementation and are now closed:

1. **`/register` requires the raw API key.** The `/api/v1/**` filter authenticates
   the instance token *before* the raw key, so left unchecked `/register` could be
   called with a token — letting a client that holds one token renew forever
   without ever re-presenting the long-lived key, defeating rotation/revocation.
   The filter now marks **how** a request authenticated (`API_KEY` vs
   `INSTANCE_TOKEN` on `Authentication.details`); `/register` rejects
   token-authenticated requests with `401 raw_api_key_required`. `/heartbeat`
   still accepts both.

2. **Tokens are revoked immediately on key lifecycle changes.** The token store
   only knew the Redis payload, so a token stayed valid up to its TTL even after
   its key was disabled/rotated/deleted — contradicting the revocation promise
   above. `mint` now indexes each token under its `keyId` (`itok:bykey:<keyId>`
   set), and `ProjectApiKeysService` calls `revokeFor(keyId)` on disable / rotate
   / delete to wipe outstanding tokens. `/heartbeat` stays Redis-only (no
   per-beat key-state read), so the ADR's scalability win is preserved; only the
   key-lifecycle path touches the index.

This is implemented incrementally on the heartbeat endpoint; other high-frequency
project-API endpoints adopt the same pattern when they appear.
