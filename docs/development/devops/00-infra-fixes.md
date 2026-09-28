# T0 — Defects in DevOps-Owned Files

Back to [task board](README.md).

Bugs in infrastructure DevOps already owns. Each one is small and keeps the team's
existing choices. Do them in one small PR before the monitoring work.

## T0.1 Back up `application_db`

`infra/backup/backup.sh` line 15 lists only three databases:

```sh
DATABASES="auth_db user_db job_db"
```

application-service data is never backed up.

- [x] Add `application_db` to `DATABASES`.
- [x] Restart `db-backup` and confirm an `application_db_*.sql.gz` dump appears:
  `docker compose -f infra/docker/docker-compose.yml exec db-backup ls -lh /backups`
- [x] Restore test: insert a row, restore the previous dump, confirm the row is gone.

## T0.2 Pin MinIO images

The team pinned MinIO in commit `aa05182`, but Compose later used `quay.io/minio/minio`
and `quay.io/minio/mc` (latest), which could not be pulled (ROADMAP B1).

- [x] Resolved by the job-service owner in PR #10 (`c18f35e`): `infra/docker/minio/Dockerfile`
  builds both from source, pinned to `RELEASE.2025-09-07T16-13-09Z` (server) and
  `RELEASE.2025-08-13T08-35-41Z` (`mc`).

## T0.3 MinIO credentials from env variables

`minio/minio123` is written literally in `minio`, `minio-init`, and `job-service`.
Every other secret follows the team pattern `${VAR:-dev-default}`.

- [x] Use `${MINIO_ROOT_USER:-minio}` and `${MINIO_ROOT_PASSWORD:-minio123}` in all
  three places, including the `mc alias set` command.
- [x] Add both to `.env.example`.

Development behaviour is unchanged; production can override the values.

## T0.4 Postgres healthcheck

The healthcheck runs `pg_isready -U herafi -d auth_db`. It fails if `.env` sets a
different `DB_USERNAME`.

- [x] Use `pg_isready -U $${POSTGRES_USER} -d auth_db` (`$$` makes the container's
  own variable be used).

## T0.5 Gateway healthcheck

`api-gateway` is the only Spring service without a healthcheck. Prometheus and the
demo depend on it being ready.

- [x] Add `wget -qO- http://localhost:8080/actuator/health | grep -q UP`, matching
  the other services.

## T0.6 Documentation

- [x] Add application-service and `application_db` to the README mermaid diagram.
- [x] README Backups section: list all four databases.

## Acceptance criteria

- The stack starts cleanly after `docker compose down -v`.
- Backups contain four databases, and one restore was tested.
- No credential literal remains in `docker-compose.yml`.
