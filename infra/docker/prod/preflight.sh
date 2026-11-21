#!/bin/sh
set -eu

file=infra/docker/prod/.env
https_file=infra/docker/prod/.env.https
[ -f "$file" ] || { echo "Missing $file (copy .env.example and fill it in)" >&2; exit 1; }
for env_key in DB_USERNAME DB_PASSWORD JWT_SECRET GATEWAY_SHARED_SECRET INTERNAL_API_KEY MINIO_ROOT_USER MINIO_ROOT_PASSWORD METRICS_USERNAME METRICS_PASSWORD KAFKA_CLUSTER_ID; do
    value=$(sed -n "s/^${env_key}=//p" "$file" | tail -n 1)
    [ -n "$value" ] || { echo "Missing $env_key in $file" >&2; exit 1; }
    case "$value" in *replace-me*) echo "Replace placeholder $env_key in $file" >&2; exit 1 ;; esac
done
secret=$(sed -n 's/^JWT_SECRET=//p' "$file" | tail -n 1)
[ "${#secret}" -ge 32 ] || { echo 'JWT_SECRET must contain at least 32 characters' >&2; exit 1; }
for asset in "$https_file" infra/docker/prod/tls/server.crt infra/docker/prod/tls/server.key; do
    [ -s "$asset" ] || { echo "Missing $asset; run make prod-https first." >&2; exit 1; }
done
command -v docker >/dev/null || { echo 'Docker Compose is required' >&2; exit 1; }
env -u HTTPS_HOST_IP -u FRONTEND_ORIGIN -u S3_PUBLIC_ENDPOINT -u TLS_CERT_SHA256 \
    docker compose -p 7irafi-prod --env-file "$file" --env-file "$https_file" \
    -f infra/docker/docker-compose.yml -f infra/docker/prod/compose.yml config --quiet
echo 'Production configuration passed local preflight.'
