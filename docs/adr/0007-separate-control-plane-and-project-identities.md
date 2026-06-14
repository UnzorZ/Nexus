# ADR-0007: Separate Control-Plane and Project Identities

Status: Accepted

## Context

Nexus has three distinct authorization contexts:

- Nexus accounts that access the dashboard and create or manage projects,
- instance administrators that can manage the whole Nexus installation,
- project users that authenticate through a project's isolated OAuth/OIDC realm.

A Nexus account may manage several projects, and a project may be managed by
several Nexus accounts. Project users have no implicit relationship with Nexus
dashboard access.

Placing all account entities in `shared` would make `shared` own business rules,
persistence, and lifecycle decisions that belong to separate modules.

## Decision

Model dashboard identity as `NexusAccount`, owned by the `admin` module.

Model instance administration as a boolean capability on `NexusAccount`, not as
a separate account type or extensible role catalog. Nexus does not plan to add
other instance-wide administrative roles.

Model project access as `ProjectMembership`, owned by the `projects` module. A
membership links a `NexusAccountId` and a `ProjectId` and carries a project role
such as `OWNER`, `ADMIN`, or `MEMBER`.

Model OAuth/OIDC end users as `ProjectUser`, owned by the `identity` module and
isolated by project.

Module-owned JPA entities do not live in `shared`. Modules reference identities
owned elsewhere using typed IDs and communicate through public application
services or domain events. Cross-module JPA associations and direct access to
another module's repository are not allowed.

## Consequences

- Nexus account credentials and sessions remain separate from project-user
  credentials and OAuth sessions.
- Promoting a Nexus account to instance administrator updates the account
  without duplicating it or creating a separate role entity.
- Project ownership and collaboration can evolve independently from global
  instance administration.
- The same email may identify a Nexus account and one or more unrelated project
  users without merging those identities.
- `shared` may contain stable typed IDs, actor references, validation primitives,
  and narrow contracts, but not `NexusAccount`, `ProjectMembership`, or
  `ProjectUser` entities.
