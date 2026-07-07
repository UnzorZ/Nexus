# Security Policy

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please report suspected vulnerabilities privately, with enough detail to reproduce
(diagram, steps, affected endpoint/version). Preferred channels:

1. **GitHub Private Vulnerability Reporting** — the "Report a vulnerability" button on
   the **Security → Advisories** tab of this repository (recommended).
2. Email the maintainer directly (replace with the project's security contact).

You should receive an initial acknowledgement within **5 business days**. We will
coordinate a fix and a coordinated disclosure timeline with you. Please do not
disclose the issue publicly until a fix is available.

## Scope

In scope:

- The Nexus backend (`apps/api`) and dashboard (`apps/web`) as shipped from `master`.
- Authentication, authorization, session, OAuth/OIDC, and secrets handling.
- Self-hosted deployments that follow [`compose.prod.yaml`](compose.prod.yaml) and set
  real secrets.

Out of scope:

- Vulnerabilities in dependencies not exploitable through Nexus (report them upstream).
- Issues that require an already-privileged (instance admin) actor to escalate within
  their own instance, unless they cross project or account boundaries.
- Spam, social engineering, or DoS without a demonstrated Nexus-specific vector.

## Supported Versions

Only the latest release line on `master` receives security fixes. Nexus is
pre-1.0 / active development; there are no backport branches yet.

## Hardening Checklist for Self-Hosters

- Generate a real JWT signing keystore; never reuse the committed dev keystore.
- Set a strong OAuth bootstrap client secret and a strong Vault master key.
- Terminate TLS at the edge or on the API origin; use secure cookies.
- Restrict `/actuator/prometheus` (reverse-proxy allowlist or a separate management
  port) — it is public by default to ease trusted-network scraping.
- Run PostgreSQL and Redis on managed or network-isolated infrastructure.
- Review module exposure and API-key scopes per project.
- See [`docs/threat-model.md`](docs/threat-model.md) for the trust model.
