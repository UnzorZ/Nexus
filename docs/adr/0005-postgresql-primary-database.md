# ADR-0005: PostgreSQL As Primary Database

Status: Accepted

## Context

Nexus will store relational data, audit records, permission metadata, module configuration, and future JSON-shaped configuration.

The project owner is familiar with MariaDB, but Nexus benefits from PostgreSQL's strong relational behavior and JSONB support.

## Decision

PostgreSQL is the primary database for Nexus.

Schema changes must be managed with Flyway migrations.

## Consequences

- Local development uses PostgreSQL through Docker Compose or a local server.
- JSON-like module configuration can use PostgreSQL-native capabilities when useful.
- The codebase should not rely on Hibernate auto-DDL for real schema evolution.
