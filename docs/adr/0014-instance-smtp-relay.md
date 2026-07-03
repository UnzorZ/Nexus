# ADR-0014: Instance-provided SMTP relay (panel-manageable)

Status: Accepted

## Context

ADR-0013 hardened the SMTP **TLS trust model** but left the ownership question
open: SMTP was configured **per project** (`project_smtp_settings`), with a single
deploy-time env fallback (`nexus.notify.smtp.*`). For a **self-hosted Nexus with
one operator** ("runs on your own server · yours, completely"), that is the wrong
default:

- **Friction + poor deliverability.** Every project admin had to bring and tune
  their own relay. Casual relays land in spam; SPF/DKIM/DMARC are almost never set
  up correctly per project.
- **Hidden operator config.** The only instance-wide SMTP (`nexus.notify.smtp.*`)
  was env-only — invisible and unmanageable from the panel, requiring a redeploy
  to change.
- **The operator already has a working relay.** `mail.unzor.dev` (Let's Encrypt,
  verified per ADR-0013) is the operator's relay and is what almost every project
  should send through.

At the same time there was **no instance-level configuration surface at all** in
the panel — instance settings were scattered across `@ConfigurationProperties` and
env vars, with no operator UI.

## Decision

1. **SMTP becomes instance-provided by default.** A new singleton
   `instance_smtp_settings` row holds the operator's relay (host/port/username/
   from, AES-GCM-encrypted password, TLS mode + pinned CA — same shape and same
   ADR-0013 hardening as the per-project table). `NotifyEmailSender.resolve`
   resolves in a fixed cascade:

   **project override → instance SMTP (DB) → env (`nexus.notify.smtp.*`) → unconfigured.**

   A project with no override transparently uses the instance relay; the per-project
   override is **retained** as an advanced escape hatch for tenants that genuinely
   need their own relay or from-domain.

2. **The DB row overrides the env default.** The env `nexus.notify.smtp.*` remains
   the deploy-time seed; once the operator sets SMTP from the panel, that value wins
   (otherwise the panel would be a no-op). Removing the panel value returns
   resolution to the env.

3. **Panel-manageable instance settings (first admin-required endpoints).** A new
   `instance` module exposes `/api/panel/v1/instance/*` — the first endpoints in
   Nexus that **require** `ROLE_INSTANCE_ADMIN` (rather than merely bypassing
   project membership). The operator panel edits:
   - **Email delivery (SMTP)** — writeable (save + test-connection, delegated to the
     `notify` module which owns SMTP).
   - **Registration policy** — writeable open/closed, enforced on `POST /accounts`.
     No lockout: the instance admin always exists once the toggle is reachable, and
     can re-open it; the bootstrap of the first admin is always allowed (ADR-0010).
   - **Module defaults** — writeable; which modules a fresh project starts with
     (override of the hardcoded `NexusModule.DEFAULT_ENABLED`, applied at
     `ProjectModuleService` read/gate time).
   - **Heartbeat defaults** — writeable instance interval/timeout, inserted as the
     middle fallback (project → instance → env) in `RegistryHeartbeatService`.

   Remain **read-only** (status only) because they are deploy-time/env or key
   material: session timeout/cookie attrs, vault master key (rotating re-encrypts
   every secret of every project), JWT keystore, frontend URL/CORS, DB/Redis.

4. **Modulith boundaries.** SMTP lives in `notify` (it owns the know-how and needs
   to resolve the relay on every send); the `instance` module consumes `notify`'s
   public SMTP service one-way. The writeable settings (registration, module
   defaults, heartbeat) live in `instance` and are read one-way by `admin`
   (registration gate), `modules` (defaults) and `registry` (heartbeat fallback).
   To avoid a cycle, `instance` does **not** depend on `modules`: the module
   catalog is a frontend concern, and module keys are stored/validated as strings.

## Consequences

- **Most projects send with zero SMTP configuration.** They inherit the operator
  relay; deliverability (SPF/DKIM/DMARC, warmed IP) is the operator's single
  responsibility, solved once.
- **Power users keep flexibility.** The per-project override (ADR-0013 hardening
  intact — SSRF guard, PUBLIC/PINNED TLS, test-connection) remains for tenants that
  need their own relay or from-domain. The project panel already shows whether it is
  using the instance relay or its own override.
- **The operator can change SMTP without a redeploy.** The panel write takes effect
  immediately (resolution reads the DB), and is audited as `instance.smtp.updated`.
- **ADR-0013 is unchanged and applies at both levels.** Instance and per-project
  SMTP use the identical TLS trust model (WebPKI-first + optional pinning + SSRF
  guard); only the *ownership/default* changed.
- **From-address is the instance's domain by default.** For self-hosted Nexus this
  is correct; multi-tenant "send from the tenant's verified domain" (SendGrid-style
  authenticated sending domains) is explicitly out of scope and a future
  enhancement, not this decision.
