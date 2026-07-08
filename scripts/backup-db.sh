#!/usr/bin/env bash
#
# Nexus — PostgreSQL backup.
#
# Produces a gzipped, timestamped logical dump of the Nexus database and prunes
# old backups beyond a retention window. Designed for operator cron; see
# docs/deployment/backups.md.
#
# Connection mode (USE_DOCKER, default "auto"):
#   - "auto" + local pg_dump on PATH → connects directly (managed/external Postgres)
#   - "auto" without local pg_dump   → runs pg_dump inside the compose 'postgres' container
#   - "no"                           → always use local pg_dump
#   - "yes"                          → always use the compose container
#
# Examples:
#   ./scripts/backup-db.sh
#   RETAIN_DAYS=14 ./scripts/backup-db.sh
#   USE_DOCKER=no PG_HOST=db.internal PG_PASSWORD=secret ./scripts/backup-db.sh
#   COMPOSE_FILE=compose.prod.yaml ./scripts/backup-db.sh
#
set -euo pipefail

PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-nexus}"
PG_DB="${PG_DB:-nexus}"
# Default matches the dev compose (POSTGRES_PASSWORD=nexus). Override in prod.
PG_PASSWORD="${PG_PASSWORD:-${NEXUS_DATASOURCE_PASSWORD:-nexus}}"

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETAIN_DAYS="${RETAIN_DAYS:-30}"

USE_DOCKER="${USE_DOCKER:-auto}"
COMPOSE_FILE="${COMPOSE_FILE:-compose.yaml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-postgres}"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
outfile="${BACKUP_DIR%/}/nexus-${stamp}.sql.gz"
tmpfile="${outfile}.tmp"

mkdir -p "${BACKUP_DIR}"

PGDUMP_OPTS=(--no-owner --no-privileges --clean --if-exists -U "${PG_USER}" -d "${PG_DB}")

if [[ "${USE_DOCKER}" == "auto" ]] && command -v pg_dump >/dev/null 2>&1; then
    mode="local pg_dump"
    PGPASSWORD="${PG_PASSWORD}" pg_dump -h "${PG_HOST}" -p "${PG_PORT}" "${PGDUMP_OPTS[@]}" \
        | gzip > "${tmpfile}"
elif [[ "${USE_DOCKER}" != "no" ]]; then
    mode="docker compose (${COMPOSE_FILE}:${COMPOSE_SERVICE})"
    if ! docker compose -f "${COMPOSE_FILE}" ps --status running --services 2>/dev/null \
            | grep -q "^${COMPOSE_SERVICE}$"; then
        echo "error: compose service '${COMPOSE_SERVICE}' is not running (file ${COMPOSE_FILE})" >&2
        echo "       start it first, or set USE_DOCKER=no + PG_HOST/PG_PASSWORD for an external DB." >&2
        exit 1
    fi
    # -e PGPASSWORD keeps the credential off the container process list.
    docker compose -f "${COMPOSE_FILE}" exec -T -e PGPASSWORD="${PG_PASSWORD}" \
        "${COMPOSE_SERVICE}" pg_dump "${PGDUMP_OPTS[@]}" | gzip > "${tmpfile}"
else
    mode="local pg_dump (forced)"
    PGPASSWORD="${PG_PASSWORD}" pg_dump -h "${PG_HOST}" -p "${PG_PORT}" "${PGDUMP_OPTS[@]}" \
        | gzip > "${tmpfile}"
fi

# Guard against a silently-empty dump (e.g. wrong DB name, auth failure past pg_dump start).
if [[ ! -s "${tmpfile}" ]]; then
    echo "error: produced an empty dump; aborting (check credentials / DB name)" >&2
    rm -f "${tmpfile}"
    exit 1
fi

mv "${tmpfile}" "${outfile}"
bytes="$(wc -c < "${outfile}" | tr -d ' ')"
echo "wrote ${outfile} (${mode}, ${bytes} bytes)"

# Retention: prune backups older than RETAIN_DAYS.
pruned="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'nexus-*.sql.gz' -mtime +"${RETAIN_DAYS}" -print -delete | wc -l | tr -d ' ')"
echo "retention: kept <= ${RETAIN_DAYS}d (pruned ${pruned} old backup(s)) in ${BACKUP_DIR}"
