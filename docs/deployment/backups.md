# PostgreSQL Backups

Nexus does **not** bundle a managed backup service. The operator is responsible for
backups of the PostgreSQL database (`project_users`, `nexus_accounts`, OAuth
consents, audit, vault secrets — everything except ephemeral Redis session state).

A reference script is provided: [`scripts/backup-db.sh`](../../scripts/backup-db.sh).
It produces a gzipped, timestamped logical dump (`pg_dump`) and prunes old backups
past a retention window. Use it as-is for a single-host deployment, or as the
reference for a managed-DB / pg_basebackup / WAL-archiving strategy.

## What is and isn't covered

- **Backed up**: everything in the Postgres database. `pg_dump --no-owner
  --no-privileges --clean --if-exists` makes the dump portable across
  environments and restorable over an existing schema.
- **Not backed up**: Redis (Spring Session, CSRF tokens, ephemeral security
  state). Losing Redis forces re-login but loses no persistent data. The JWT
  keystore and Vault master key are **secrets** managed out-of-band (see
  [ADR-0011](../adr/0011-persistent-jwt-signing-keys.md) and
  [jwt-signing-keys.md](jwt-signing-keys.md)); back them up separately and
  securely — **without the Vault master key, encrypted secrets cannot be
  decrypted after a restore.**

## Connection modes

`USE_DOCKER` controls how `pg_dump` runs:

| `USE_DOCKER` | Behavior |
|---|---|
| `auto` (default) + `pg_dump` on PATH | connect directly to `PG_HOST:PG_PORT` |
| `auto` without `pg_dump` | run `pg_dump` inside the compose `postgres` container |
| `yes` | always use the compose container |
| `no` | always use local `pg_dump` (managed/external Postgres) |

Credentials default to the dev compose values (`nexus` / `nexus`). In prod,
override via `PG_PASSWORD` (or `NEXUS_DATASOURCE_PASSWORD`).

## Run manually

```bash
# Dev (compose postgres running): works with no env.
./scripts/backup-db.sh

# Tune retention; back up to a mounted volume.
RETAIN_DAYS=14 BACKUP_DIR=/var/backups/nexus ./scripts/backup-db.sh

# External/managed Postgres (no docker involvement).
USE_DOCKER=no PG_HOST=db.internal PG_PASSWORD=secret ./scripts/backup-db.sh

# Prod compose stack.
COMPOSE_FILE=compose.prod.yaml PG_PASSWORD="$POSTGRES_DB_PASSWORD" ./scripts/backup-db.sh
```

Output:

```
wrote ./backups/nexus-20260708T120000Z.sql.gz (docker compose (compose.yaml:postgres), 18432 bytes)
retention: kept <= 30d (pruned 0 old backup(s)) in ./backups
```

## Schedule with cron

Example: daily at 03:17 (off the round hour to spread load), keeping 30 days.

```cron
17 3 * * *  cd /opt/nexus && PG_PASSWORD=... ./scripts/backup-db.sh >> /var/log/nexus-backup.log 2>&1
```

For prod compose, add `COMPOSE_FILE=compose.prod.yaml`. Ensure the cron user has
read access to the `.env` (for `POSTGRES_DB_PASSWORD`) and write access to
`BACKUP_DIR`. Verify the log periodically — a silent failure (full disk, wrong
password) is the realistic failure mode, not the script itself.

## Restore

The dump includes `--clean --if-exists` DROP statements, so it can be restored
into a database that already has (partial) schema without manual cleanup. Target
a **stopped** API first to avoid writes during restore.

```bash
# Into the dev compose postgres.
gunzip -c ./backups/nexus-<stamp>.sql.gz \
  | docker compose -f compose.yaml exec -T -e PGPASSWORD=nexus postgres \
      psql -U nexus -d nexus

# Into an external DB.
gunzip -c ./backups/nexus-<stamp>.sql.gz \
  | PGPASSWORD=secret psql -h db.internal -U nexus -d nexus
```

After restoring, bring the API back up. Flyway runs in `validate` mode
(`spring.flyway.*`); if the restored schema is behind the code's migrations,
apply pending migrations on a temporary `update` run or by re-running Flyway
manually against the restored DB before pointing prod traffic at it.

## Verifying a backup

A backup you haven't restored is an assumption, not a backup. Periodically restore
into a throwaway database and confirm a known record (e.g. `SELECT count(*) FROM
nexus_accounts;`) round-trips:

```bash
docker run --rm -i -e PGPASSWORD=nexus postgres:17-alpine \
  psql -h host.docker.internal -U nexus -d nexus_verify < <(gunzip -c ./backups/nexus-<stamp>.sql.gz)
```

## Alternatives

For larger/busier deployments prefer physical backups: `pg_basebackup` +
continuous WAL archiving (PITR) via `pgBackRest`, Barman, or your managed-DB
provider's snapshots. These give point-in-time recovery and avoid the lock/IO
cost of a full logical dump. `scripts/backup-db.sh` is the simple, dependency-free
baseline — not a replacement for these when RPO/RTO matter.
