# Nexus Dashboard

Next.js admin dashboard for Nexus: panel login, project management, API keys,
module toggles, permissions/roles, audit browsing, and heartbeat/status views.

See the repository root [`AGENTS.md`](../../AGENTS.md) for the frontend layout
conventions (`lib/api`, `features/<domain>`) and [`CONTRIBUTING.md`](../../CONTRIBUTING.md)
for the overall contribution workflow.

## Development

```bash
npm install
npm run dev
```

Runs at [http://localhost:3000](http://localhost:3000). The backend API must be
running separately (see the root [README](../../README.md#quick-start)).

By default the app is run alongside PostgreSQL, Redis, and the API via Docker
Compose from the repository root. To run the frontend on the host instead:

```bash
docker compose up -d postgres redis
cd apps/web && npm install && npm run dev
```

The host-run frontend reads `apps/web/.env.local`, not the repository-root `.env`.

## Build and quality checks

```bash
npm run lint
npm run build
```

Both are run in CI on every pull request.
