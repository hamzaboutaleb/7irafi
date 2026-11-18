#!/bin/sh
# Automated Postgres backup for 7irafi.
#
# Runs inside the db-backup container on a fixed interval. Dumps every service
# database separately rather than the whole cluster: each service owns its own
# schema, so they must be restorable independently.
#
# Environment (all supplied by docker-compose from infra/docker/.env):
#   PGHOST PGUSER PGPASSWORD  - connection
#   BACKUP_DIR                - where dumps land (a named volume)
#   BACKUP_INTERVAL_SECONDS   - how often to run
#   BACKUP_RETENTION_DAYS     - dumps older than this are deleted
set -eu

DATABASES="auth_db user_db job_db application_db chat_db notification_db analytics_db"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"

for db in $DATABASES; do
    target="$BACKUP_DIR/${db}_${stamp}.sql.gz"

    # --clean --if-exists makes the dump safe to replay over an existing
    # database, which is what restore.sh relies on.
    if pg_dump --clean --if-exists --no-owner --dbname "$db" | gzip > "$target"; then
        echo "[backup] $(date -u +%FT%TZ) wrote $target ($(wc -c < "$target") bytes)"
    else
        echo "[backup] $(date -u +%FT%TZ) FAILED for $db" >&2
        rm -f "$target"
    fi
done

# Retention. Without this the volume grows until the disk fills, which turns a
# backup system into an outage.
deleted="$(find "$BACKUP_DIR" -name '*.sql.gz' -type f -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)"
[ "$deleted" -gt 0 ] && echo "[backup] pruned $deleted dump(s) older than ${RETENTION_DAYS}d"

exit 0
