#!/bin/sh
set -eu

minio server /data --console-address ":9001" &
MINIO_PID=$!

trap 'kill "$MINIO_PID" 2>/dev/null || true' TERM INT

until mc alias set local http://127.0.0.1:9000 \
    "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"; do
    if ! kill -0 "$MINIO_PID" 2>/dev/null; then
        exit 1
    fi
    sleep 2
done

# Creating Buckets.
mc mb --ignore-existing local/jobs
mc mb --ignore-existing local/avatars

# Configuring lifecycle rule.
if ! mc ilm rule ls local/jobs | grep -q 'cleanup=pending'; then
    mc ilm rule add \
        --tags "cleanup=pending" \
        --expire-days 1 \
        local/jobs
fi

wait "$MINIO_PID"