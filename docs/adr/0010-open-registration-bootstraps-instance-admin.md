# ADR-0010: Open registration bootstraps the single instance admin

Status: Accepted

## Context

The Nexus panel exposes account registration as a public endpoint
(`POST /api/panel/v1/accounts`, `permitAll` in `PanelSecurityConfiguration`).
An instance needs exactly one instance administrator to manage the control plane.

`CreateNexusAccountService` delegates the first-admin decision to
`InstanceAdminBootstrapService.grantInstanceAdminIfMissing(...)`, which:

- takes a PostgreSQL transaction-scoped advisory lock
  (`pg_advisory_xact_lock`, see `NexusAccountRepository`), and
- grants `instanceAdmin = true` to the account being created **only if no other
  account already has that flag** (`existsByInstanceAdminTrue`).

The database additionally enforces the single-admin invariant directly with a
**partial unique index** (`V2__create_projects_accounts_and_users.sql`):

```sql
CREATE UNIQUE INDEX uk_nexus_accounts_one_instance_admin
    ON nexus_accounts (instance_admin)
    WHERE instance_admin;
```

So at most one account can ever hold `instanceAdmin = true`, and the first
account created on a fresh instance becomes it. This is sometimes described as a
"trust on first use" (TOFU) bootstrap.

A reviewer can reasonably flag this as a squatting risk: if a freshly deployed
instance is reachable before the intended operator registers, an attacker who
registers first becomes the sole instance administrator.

## Decision

This behavior is **intentional and accepted**. It is how Nexus bootstraps its
single instance administrator, not a defect.

- Public registration plus first-account-wins is the chosen bootstrap mechanism.
- The partial unique index is the source of truth for "exactly one instance
  admin"; it is a deliberate design constraint, not an accident.
- The advisory lock serializes concurrent first registrations so the outcome is
  deterministic.

## Consequences

- The operator must register the first account **before** exposing a fresh
  instance to an untrusted network. Deployment runbooks should call this out.
- There can be only one instance administrator at a time. Transferring the role
  requires revoking it from the current admin (which also revokes their panel
  sessions, see ADR-0008) and then granting it to another account; there is no
  API for this yet.
- This decision should not be re-reported as a security bug in future reviews.
  If the bootstrap model must change (e.g. an out-of-band seed admin, a setup
  token, or multiple admins), that is a new decision that supersedes this ADR.
