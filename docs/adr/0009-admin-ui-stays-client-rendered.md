# ADR-0009: Admin UI stays client-rendered; instanceAdmin is a UX hint

Status: Accepted

## Context

Instance administration is a single boolean flag on `NexusAccount`
(`instanceAdmin`), not a role system. The panel Next.js app calls the API
directly from the browser over the cookie session (see AGENTS.md), and renders
admin-only controls conditionally based on the `instanceAdmin` field returned by
`GET /api/panel/v1/me` and `POST /api/panel/v1/session/login` (the
`NexusAccountDetails` DTO).

Because that flag is ordinary client-side state, a user can manipulate their own
browser state (React state, devtools) to make admin UI appear even when their
account is not an instance administrator. This is a cosmetic **UI spoof**: it
reveals controls but cannot execute anything, because every admin action is
authorized server-side. The session itself is not client-manipulable — it is
server-side Spring Session in Redis keyed by an opaque `JSESSIONID`
(ADR-0008) — so there is no path to privilege escalation through the client.

A considered alternative was to render the admin surface server-side (SSR) so a
non-admin never receives admin markup at all. That would require the Next.js
server to know the caller's admin status, which means calling `/me` server-side
by forwarding the host-only `JSESSIONID` to the API — i.e. the Next.js BFF/proxy
pattern that AGENTS.md deliberately avoids ("Nexus currently calls the backend
directly from the browser").

## Decision

Keep the admin UI client-rendered. Do **not** move it to SSR, and do **not**
reintroduce a Next.js BFF/proxy to support it.

The `instanceAdmin` flag sent to the frontend is a **UX hint for rendering only**,
never an authorization decision. All authorization for admin actions happens
server-side, on every request. The cosmetic client-side UI spoof is accepted as a
non-issue because the server is the authority on every privileged operation.

Accordingly, `instanceAdmin` stays in `NexusAccountDetails`; the panel needs it to
render admin controls, and withholding it would break the UX.

## Consequences

- A non-admin can make admin UI appear locally. This is accepted and harmless; no
  client-side obfuscation is required or valuable.
- Engineering effort belongs in server-side authorization, not in hiding admin UI.
- `instanceAdmin` remains a boolean flag on the account (not a role system),
  consistent with AGENTS.md.
- Instance-admin status is projected into the Spring Security authority at login
  time (`NexusAccountAuthorityResolver`); staleness when the flag is removed is
  handled by session revocation (ADR-0008), not by the authority layer.
- If a future `/api/admin/**` surface makes "non-admins must not even learn that
  admin screens exist" a real requirement, revisit: gate a dedicated admin route
  segment server-side, which would force re-evaluating the no-BFF decision. Until
  then, CSR + flag + server-side checks is the chosen pattern.
