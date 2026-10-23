#!/bin/sh
set -e

# Prometheus cannot expand environment variables inside prometheus.yml, so the
# gateway scrape credentials are written to files that the config references via
# username_file and password_file. They must match the gateway's METRICS_USERNAME
# and METRICS_PASSWORD. /run/secrets is a tmpfs, so they never touch the disk.
printf '%s' "${METRICS_USERNAME}" > /run/secrets/metrics_username
printf '%s' "${METRICS_PASSWORD}" > /run/secrets/metrics_password

exec /bin/prometheus "$@"
