# Production deployment runbook

A checklist-driven runbook for self-hosting Nexus in production. The detailed
topics each have their own page — this ties them together into a single path from
"empty host" to "monitored, backed-up, upgradable deployment".

> If you just want to try Nexus locally, use the **Quick Start** in the
> [README](../../README.md) — do **not** use these steps (they refuse dev secrets).

## TL;DR

```bash
cp .env.example .env                      # then edit: set EVERY real secret
# generate a JWT keystore (see §2) and put it on a path the API container can read
docker compose -f compose.prod.yaml up -d --build
curl -fsS http://localhost:8080/actuator/health/readiness   # expect {"status":"UP"}
```

`compose.prod.yaml` runs the API with `SPRING_PROFILES_ACTIVE=prod`. Under that
profile, `IdentityStartupGuard` and `VaultCrypto` **abort startup** if the dev
JWK keystore or the dev Vault master key are still in use, and `${VAR:?...}`
makes `docker compose` itself fail fast on a missing required secret. So the
stack refuses to boot until it is properly configured.

## Prerequisites

- A host (or orchestrator) with Docker + Docker Compose.
- TLS termination at the edge (reverse proxy, load balancer, or Ingress). Nexus
  itself speaks HTTP; terminate TLS in front.
- Managed or network-isolated **PostgreSQL 17** and **Redis 8**, or the
  containers in `compose.prod.yaml`.
- A way to manage secrets out of band (secrets manager, sealed secrets, etc.).

## 1. Secrets checklist

Generate or obtain each of these before first boot. None are optional in prod.

| Secret | How | Env var(s) |
|---|---|---|
| **JWT signing keystore** (RSA) | `keytool`, see [§2](#2-jwt-signing-keys) | `NEXUS_OAUTH_JWK_KEYSTORE_LOCATION/PASSWORD/ALIAS/KEY_PASSWORD` |
| **Vault master key** | 32 random bytes (base64/hex) | `NEXUS_VAULT_MASTER_KEY` |
| **OAuth bootstrap client secret** | random string | `NEXUS_OAUTH_BOOTSTRAP_CLIENT_SECRET` |
| **PostgreSQL password** | random string | `POSTGRES_DB_PASSWORD` (+ `NEXUS_DATASOURCE_*`) |
| **Frontend / API base URLs** | your HTTPS origins | `NEXUS_FRONTEND_BASE_URL`, `NEXT_PUBLIC_NEXUS_API_BASE_URL` |

> The Vault master key encrypts project secrets at rest (AES-256-GCM). **If you
> lose it, encrypted secrets cannot be recovered after a restore** — back it up
> separately and securely (see
> [PostgreSQL backups](backups.md#what-is-and-isnt-covered)).

## 2. JWT signing keys

Nexus signs access/ID tokens with an RSA key loaded from a PKCS12 keystore
(ADR-0011). Generate one (RSA 3072, SHA384withRSA, 10y validity) and mount it
read-only into the API container:

```bash
keytool -genkeypair \
  -alias nexus-prod-key \
  -keyalg RSA -keysize 3072 -sigalg SHA384withRSA \
  -keystore /etc/nexus/jwt-keystore.p12 \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" \
  -dname "CN=nexus, O=YourOrg" -validity 3650
chmod 600 /etc/nexus/jwt-keystore.p12
```

Then point the four `NEXUS_OAUTH_JWK_*` variables at it
(`file:/etc/nexus/jwt-keystore.p12`). Verify with the readiness endpoint — it
must report `keystoreConfigured: true` (not ephemeral). Rotation is deliberate
and non-overlapping; see [JWT signing keys](jwt-signing-keys.md) for the full
generation, verification, and rotation procedure.

## 3. PostgreSQL & Redis

- **PostgreSQL** is the durable source of truth (users, OAuth consents, audit,
  vault secrets, …). Run it managed or on isolated infra; back it up (see
  [PostgreSQL backups](backups.md)).
- **Redis** holds Spring Session, CSRF tokens, and bounded ephemeral security
  state. Losing it forces re-login but loses no persistent data. Bind it to a
  private network / loopback. Sizing, persistence, and eviction notes:
  [Redis](redis.md).

`compose.prod.yaml` wires both with healthchecks and named volumes; the Redis
config sets `appendonly`, `appendfsync everysec`, a memory cap, and the
keyspace-events Nexus needs for session expiry.

## 4. Bring it up

```bash
cp .env.example .env        # edit: real secrets only
docker compose -f compose.prod.yaml up -d --build
```

Smoke checks:

```bash
docker compose -f compose.prod.yaml logs -f api   # look for "Started NexusApplication"
curl -fsS http://localhost:8080/actuator/health           # {"status":"UP"}
curl -fsS http://localhost:8080/actuator/health/readiness # jwkSigningKey must be UP
```

Then open the dashboard, register the **first Nexus account** (it becomes the
instance admin), and create your first project.

## 5. Monitoring

- **Health/readiness:** `GET /actuator/health` and `/actuator/health/readiness`
  are public; everything else under `/actuator/**` requires HTTP Basic.
- **Metrics:** `GET /actuator/prometheus` is **public by default** to ease
  scraping. For a public deployment, restrict it (reverse-proxy allowlist, a
  separate `management.server.port`, or remove it from `permitAll`) — see the
  [threat model](../threat-model.md) open risks.
- **Rate limiting:** app-level per-IP buckets guard public auth endpoints
  (`nexus.ratelimit.*`). Tune or disable as needed; rely on a front proxy's
  connection limits elsewhere.
- **Audit log growth:** the `audit_log` table is auto-purged daily to
  `nexus.audit.retention.retention-days` (default 90; `<=0` disables). Project
  members can also stream an NDJSON export from the panel
  (`GET /api/panel/v1/projects/{id}/audit/export`).

## 6. Backups & restore

PostgreSQL is the only stateful dependency you must back up (Redis is
ephemeral). A reference script ships in the repo:

```bash
./scripts/backup-db.sh                       # dev compose: works with no env
RETAIN_DAYS=14 BACKUP_DIR=/var/backups/nexus ./scripts/backup-db.sh
```

Schedule it (cron), test restores periodically, and keep off-host copies. The
JWT keystore and Vault master key are **secrets** — back them up separately and
securely (without the master key, encrypted vault secrets are unrecoverable).
Full procedure (modes, cron, restore, verify): [PostgreSQL backups](backups.md).

## 7. Upgrades & migrations

- Nexus uses **Flyway** in `validate` mode. On a normal upgrade, start the new
  image against the existing DB — pending migrations apply, then the app boots.
- After **restoring** an older dump, the restored schema may be behind the code;
  run a temporary `update` pass (or apply Flyway manually) before pointing prod
  traffic at it (see [backups §Restore](backups.md#restore)).
- Keep the `oauth2_authorization` rows in mind on rotation: in-flight access
  tokens signed by a retired key stop validating until the client refreshes
  ([JWT signing keys §Rotation](jwt-signing-keys.md)).

## 8. Before going live

Treat a deployment as production-ready only once all of the following hold: a
real JWT keystore is mounted and readiness reports `keystoreConfigured: true`;
the Vault master key, bootstrap client secret, and database password are all
real values, not dev defaults; TLS terminates at the edge with cookies set to
`Secure` / the correct `SameSite` for your domain; PostgreSQL is backed up on a
schedule and a restore has actually been tested; `/actuator/prometheus` is
restricted or intentionally left public on a trusted network; the front proxy
enforces connection and rate limits in front of the app; and the [threat
model](../threat-model.md) has been reviewed against your deployment.

## See also

- [JWT signing keys](jwt-signing-keys.md) · [Redis](redis.md) ·
  [PostgreSQL backups](backups.md)
- [Threat model](../threat-model.md) · [Accounts & project users](../auth/accounts-and-project-users.md)
- [Identity module](../modules/identity.md) (per-project OAuth/OIDC, `permissions` claim)
- [Reference resource-server app](../../examples/spring-client-app/README.md)
