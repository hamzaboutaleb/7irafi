#!/bin/sh
# Backup loop. Takes one backup immediately on start (so a fresh stack has a
# dump within seconds rather than after the first full interval), then sleeps.
set -eu

INTERVAL="${BACKUP_INTERVAL_SECONDS:-3600}"

echo "[backup] starting; interval=${INTERVAL}s retention=${BACKUP_RETENTION_DAYS:-7}d"

while true; do
    /scripts/backup.sh || echo "[backup] cycle failed, will retry next interval" >&2
    sleep "$INTERVAL"
done
