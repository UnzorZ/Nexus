# Contributing to Nexus

Nexus is a modular-monolith control plane (Spring Boot backend + Next.js dashboard).
This guide covers the setup and conventions you need before opening a pull request.

## License

By contributing, you agree that your contributions are licensed under the project's
[AGPL-3.0](LICENSE) license, on the same terms as the rest of the codebase.

## Development Setup

Requirements: Java 21, Docker, Node.js 22.

```bash
# PostgreSQL + Redis + the Next.js dev container
docker compose up -d

# Spring Boot API
./gradlew :apps:nexus-api:bootRun
```

Dashboard: http://localhost:3000 — API: http://localhost:8080

For host-run frontend, see the [README](README.md).

## Architecture Rules (read before touching backend code)

Nexus uses **Spring Modulith** with strict module boundaries. Two rules that are easy
to get wrong:

1. **Cross-module communication goes through the `shared` package** (e.g.
   `shared.audit` events), not through direct imports between feature modules. The
   canonical pattern is *publish an event in `shared.audit`, handle it with an
   `@EventListener` in the consuming module* — see `InstanceWentOffline` /
   `InstanceOfflineNotifier`.
2. **Do not introduce a new `@NamedInterface` lightly.** A new cross-module
   `@NamedInterface` makes Modulith AOP-proxy `GenericFilterBean` filters
   (CGLIB), which breaks MockMvc initialization with a null-logger NPE. Put
   cross-module constants/types in an *already-exposed* package (e.g. `shared`).

Authorization is **fail-closed by default**. Never widen a security matcher without a
matching test.

## Frontend (Next.js)

> This is **not** the Next.js you know. Before writing frontend code, read the
> relevant guide under `apps/web/node_modules/next/dist/docs/` — APIs, conventions,
> and file structure differ from earlier versions. (See `apps/web/AGENTS.md`.)

The dashboard uses TanStack Query with a centralized data layer
(`apps/web/src/lib/api/{routes.ts,queryKeys.ts,client.ts,csrf.ts}`) and a
feature-folder convention (`src/features/<domain>/{api.ts,queries.ts}`). Mirror the
existing patterns (e.g. `features/session/`) when adding endpoints.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/) with a scope:

```
feat(identity): add email-verification token flow
fix(notify): escape app name in offline email body
```

Keep commits focused; one logical change per commit.

## Tests and Quality

```bash
./gradlew :apps:nexus-api:test      # backend (includes Testcontainers + Modulith canary)
cd apps/web && npm run lint && npm run build
```

CI (`.github/workflows/ci.yml`) runs both on every pull request — keep it green.

## Pull Requests

- Reference the issue / milestone (e.g. `Closes #N`).
- Describe *what* changed and *why*, plus how you tested it.
- Call out security-relevant changes explicitly (they get extra review).
