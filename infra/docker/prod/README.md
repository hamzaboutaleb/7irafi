# Single-host production preparation

The production Compose overlay builds the existing backend Dockerfiles and a static Angular/Nginx frontend. It uses the separate `7irafi-prod` Compose project and runs behind an **external HTTPS reverse proxy**. Only the frontend HTTP port and MinIO API port bind to loopback; publish each through your TLS proxy. The frontend proxy forwards API requests and the three WebSocket paths to their owning services.

## Before the first deployment

1. Install Docker Compose 2.24.4 or newer (the overlay uses `!reset` and `!override`).
2. Copy `infra/docker/prod/.env.example` to `infra/docker/prod/.env` and replace every placeholder. Use a distinct production PostgreSQL volume and keep `KAFKA_CLUSTER_ID` stable while reusing the Kafka volume. Never commit `.env`.
3. Configure an HTTPS proxy for `FRONTEND_ORIGIN` to `127.0.0.1:FRONTEND_PROXY_PORT` (default 8088). Forward `Host` and `X-Forwarded-Proto`.
4. Configure a separate HTTPS media origin (`S3_PUBLIC_ENDPOINT`) to `127.0.0.1:MINIO_PROXY_PORT` (default 9000). Preserve the request host and path so signed object URLs work.
5. Configure a copy of database backups **off this host**. The included `postgres-backups` Docker volume protects against a database volume failure, but not loss of the host. Verify a restore using the existing `infra/backup/restore.sh` in a disposable environment.
6. Run `make prod-preflight`, then `make test` on a machine with the required test database, then `make prod-up`. `prod-up` creates missing logical databases on an existing PostgreSQL volume; Flyway applies each service's pending migrations at startup.
7. Check `make prod-ps`, the public `/status` page, registration, job posting, applying, and messaging. Use `make prod-logs` if a service is unhealthy.

For later releases, run `make prod-backup` before `make prod-up`. Restore is intentionally manual because it overwrites data. The `prod-up` target builds the current source on the deployment host; use a CI image registry and pinned image digests before scaling this beyond a single host.

## Data setup

The Postgres init script creates one logical database per data-owning service. Job skills/cities and professional categories/cities are reference data inserted by versioned Flyway migrations. They are not a separate repeatable `seed` command. Add disposable demo records only in a development-only script; never run those against production.

## Limits

The external TLS proxy, media domain, and off-host backup destination are deployment-specific and must be provided by the operator. Observability services are opt-in with `make dev-observability`; production monitoring and alert delivery need their own operational configuration. No application or backend source is changed by this overlay.
