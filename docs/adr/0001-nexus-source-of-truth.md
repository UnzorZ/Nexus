# ADR-0001: Nexus Is The Source Of Truth

Status: Accepted

## Context

Nexus centralizes authentication, authorization, project configuration, API keys, module activation, and audit for multiple applications.

If an application made independent authorization decisions after losing contact with Nexus, Nexus would stop being the authority and permissions could drift.

## Decision

Nexus is the source of truth for project identity, API key validation, project-scoped users, permission assignment, and permission resolution.

When Nexus is unavailable, dependent authentication and authorization flows must fail closed unless a valid short-lived snapshot explicitly allows a local SDK decision.

## Consequences

- Applications must treat Nexus as an infrastructure dependency.
- SDK caches must use short TTLs and clear invalidation rules.
- Expired permission snapshots cannot be used to grant access.
- Operational monitoring for Nexus availability is required before production use.
