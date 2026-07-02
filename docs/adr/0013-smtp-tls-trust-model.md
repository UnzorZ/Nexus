# ADR-0013: SMTP TLS trust model for a multi-tenant instance

Status: Accepted

## Context

Nexus sends a project's notification email over SMTP using **that project's own
relay** (host/port/user/password, per-project with a global fallback — ADR-family
around the `notify` module). The relay is chosen by the project admin: Gmail,
SendGrid, Mailgun, SES, or the admin's own server. In a **public, multi-tenant
instance** we therefore do **not** control the network path between Nexus and
each tenant's SMTP server, and we must assume a network attacker (proxy/APM
logging, DNS poisoning, BGP) can sit on that path.

The original `NotifyEmailSender` set `mail.smtp.ssl.trust=*` — i.e. **trust any
server certificate**. That made STARTTLS opportunistic-but-unverified: the
connection is encrypted, but identity is not checked, so a MITM can present any
certificate and intercept the `AUTH LOGIN` that carries the project's SMTP
username and password. For a public instance that is an unacceptable credential-
disclosure path that affects **every** tenant.

The `*` was a dev shortcut forced by a concrete finding: the test relay
`mail.unzor.dev` presents a **Cloudflare Origin Certificate**, which by design
is trusted only by Cloudflare's edge and is not in any standard truststore
(`openssl s_client` → *unable to verify the first certificate*). Cloudflare does
not proxy SMTP and does not issue publicly-trusted certificates installable on
an origin, so an Origin cert is functionally a self-signed cert for an SMTP
client.

## Decision

Adopt a **WebPKI-first trust model** with an opt-in per-project escape hatch, and
harden the host field against SSRF. Specifically:

1. **PUBLIC mode (default) — verify against public CAs.** Remove
   `mail.smtp.ssl.trust=*`; leave the JVM default truststore (WebPKI) in charge,
   set `mail.smtp.starttls.required=true` (refuse cleartext downgrade) and
   `mail.smtp.ssl.checkserveridentity=true` (enforce CN/SAN hostname match).
   This works with no per-project config for Gmail/SendGrid/Mailgun/SES and any
   server with a public certificate (e.g. Let's Encrypt).

2. **PINNED mode — trust only an uploaded CA, per project.** For a server with a
   self-signed or private-CA certificate the admin pastes/uploads the CA (PEM);
   Nexus builds a custom `SSLContext` whose `TrustManager` trusts only that CA,
   and injects its `SSLSocketFactory` via the `mail.smtp.ssl.socketFactory`
   property. Angus Mail honours an `SSLSocketFactory` **instance** on that
   property (verified in `SocketFetcher` bytecode), so each project gets its own
   isolated trust store with no global/static wiring. This is safe in
   multi-tenant because a project's pinned CA only governs connections to *that
   project's configured host* — uploading a CA cannot let one tenant impersonate
   another's server.

3. **SSRF host guard.** The `host` field is user-controlled and Nexus makes an
   outbound connection to it, so a malicious tenant could otherwise point the
   relay at internal services (e.g. `169.254.169.254` cloud metadata,
   `127.0.0.1`, RFC1918 ranges). `SmtpHostGuard` resolves the host and rejects
   any address that is wildcard, loopback, link-local, site-local, multicast, or
   IPv6 unique-local (`fc00::/7`), at save and at every connect/send.

4. **Do NOT make Nexus a CA.** We explicitly reject "Nexus generates the
   certificate you install." Without full domain-validated issuance (DNS-01 /
   HTTP-01, CT logging — i.e. reinventing Let's Encrypt) signing whatever a
   tenant asks for lets one tenant obtain a trusted cert for **another tenant's**
   host and MITM it on the network path — a cross-tenant vector that plain
   WebPKI does **not** have (a tenant cannot get a public cert for a domain they
   do not control). It also concentrates all tenants' trust in a single in-app
   CA master key. WebPKI + optional pinning achieves the same UX without that
   risk.

5. **Connection-check endpoint.** `POST .../notify/smtp/test-connection` opens
   the transport, negotiates STARTTLS (verifying the cert) and authenticates
   **without sending mail**, returning `{ok, message}` so the panel can tell the
   admin immediately why a relay won't work (untrusted cert → switch to PINNED /
   get LE; auth failure; unreachable host; unsafe host).

## Consequences

- **Most tenants need no certificate configuration.** Public providers and any
  server with a public cert work out of the box. The Cloudflare-Origin case is
  no longer papered over with `*`: that relay must either obtain a public cert
  (Let's Encrypt) or use PINNED mode with the Origin CA uploaded.
- **Self-signed servers are a deliberate, per-project choice**, audited as part
  of `notify.smtp.updated`, and isolated to that project's trust manager.
- **STARTTLS is mandatory**: a server that cannot negotiate verified TLS will
  fail to send (and fail `test-connection` loudly) rather than silently falling
  back to cleartext.
- **Residual — DNS rebinding.** The guard resolves the host at call time; a
  malicious DNS with a zero TTL could in principle resolve to a public address
  for the check and a private one for the connection. This is accepted as a base
  mitigation; fully closing it would require pinning the resolved IP onto the
  socket, which JavaMail does not expose cleanly. The check still defeats the
  static/literal internal-host cases that are the realistic SSRF vector.
- **Operational**: the existing `mail.unzor.dev` relay (Cloudflare Origin) will
  fail until it either presents a Let's Encrypt cert or the project switches to
  PINNED and uploads the Origin CA. This is the intended, secure behaviour.
