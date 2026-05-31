# ADR-0004: API Keys Identify Projects

Status: Accepted

## Context

Backend applications need a simple way to authenticate to Nexus and identify which Nexus project they belong to.

One project may have multiple deployments, integrations, or rotated credentials.

## Decision

Each project may own multiple API keys. An API key identifies exactly one project.

API keys must never be stored in plain text. Nexus stores a safe prefix for lookup/display and a hash for validation.

## Consequences

- Backend-to-Nexus calls can resolve project context before executing module logic.
- Key rotation can happen without replacing the project.
- Audit must record key creation, rotation, disabling, expiration, and failed validation attempts without leaking secrets.
