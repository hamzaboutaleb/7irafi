#!/bin/sh
# Disaster recovery for 7irafi. Restores ONE database from ONE dump.
#
#   docker compose -f infra/docker/docker-compose.yml exec db-backup \
#       /scripts/restore.sh user_db /backups/user_db_20260905T120000Z.sql.gz
#
# Deliberately NOT automatic: restoring overwrites live data, so it is always a
# human decision. List available dumps with:
#   docker compose -f infra/docker/docker-compose.yml exec db-backup ls -lh /backups
set -eu

if [ $# -ne 2 ]; then
    echo "usage: restore.sh <database> <path-to-dump.sql.gz>" >&2
    exit 2
fi

db="$1"
dump="$2"

[ -f "$dump" ] || { echo "no such dump: $dump" >&2; exit 1; }

echo "[restore] restoring $db from $dump"
echo "[restore] this OVERWRITES the current contents of $db"

# The dump was taken with --clean --if-exists, so it drops and recreates each
# object as it goes. ON_ERROR_STOP makes psql abort on the first failure instead
# of ploughing on and leaving a half-restored database.
gunzip -c "$dump" | psql --dbname "$db" --set ON_ERROR_STOP=on --quiet

echo "[restore] $db restored successfully"
