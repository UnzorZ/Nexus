# ADR-0003: Positive Permissions In MVP

Status: Accepted

## Context

Nexus permissions are project-scoped string flags declared by applications and assigned to users or roles.

Negative permissions add expressive power, but they also make resolution order, explainability, UI design, and testing more complex.

## Decision

The MVP supports positive permissions only.

Supported matching:

- exact permission, such as `orders.cancel`,
- namespace wildcard, such as `orders.*`,
- global wildcard, `*`.

Negative permissions are deferred until the permission resolver, audit explanations, and dashboard UX can make them understandable.

## Consequences

- Effective permissions are additive.
- Permission checks are easier to reason about and test.
- Deny-by-default still applies when no positive permission matches.
