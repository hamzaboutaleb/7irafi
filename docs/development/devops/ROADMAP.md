# DevOps Roadmap and Work Log

Every entry records **what** was built, **how**, and **why**, with the commit that
carries it. `docs/` is ignored by git; this folder is committed with `git add -f` at
the lead's request.

Board: [README.md](README.md)

## Roadmap

| Phase | Tasks | Status | Branch |
| --- | --- | --- | --- |
| T0 Infra fixes | T0.1–T0.6 | ✅ Done (T0.2 by the job-service owner in PR #10) | `devops` |
| T1 Metrics in services | T1.1–T1.7 | ⏳ Next | — |
| T2 Request ID + JSON logs | T2.1–T2.8 | Not started | — |
| T3 Prometheus | T3.1–T3.3 | Not started | — |
| T4 Grafana | T4.1–T4.4 | Not started | — |
| T5 Alerting | T5.1–T5.4 | Not started | — |
| T6 ELK | T6.1–T6.5 | Not started | — |
| T7 Retention (ILM) | T7.1–T7.4 | Not started | — |
| T8 README + demo | T8.1–T8.6 | Not started | — |

## Blockers and questions for the team

| # | Raised | For | Issue | Status |
| --- | --- | --- | --- | --- |
| B1 | 2026-09-28 | job-service owner | MinIO images cannot be pulled. `quay.io/minio/minio` and `quay.io/minio/mc` return `unauthorized` / `no such manifest` for anonymous pulls, including `latest` and the old pinned release tags. A fresh machine cannot start the stack. The switch to `quay.io` was the owner's decision (`696afae`), so the new image source is theirs to choose. | Closed: PR #10 (`c18f35e`) builds `minio` and `mc` from source at pinned release tags |
| B2 | 2026-09-28 | lead | `AGENTS.md` says Java 25; all `pom.xml` files use 21 (proposal P4). | Open |

## Log

### 2026-09-28 — T0 Infra fixes

Branch `devops`, from `main` at `1b49f47`.

**T0.1 Back up `application_db`** — commit `727bf20`

- What: `infra/backup/backup.sh` dumps `application_db` too.
- How: added it to the `DATABASES` list.
- Why: application-service data was never backed up.

**T0.1b `db-backup` credential defaults** (found during T0) — commit `727bf20`

- What: `db-backup` uses `${DB_USERNAME:-herafi}` / `${DB_PASSWORD:-herafi}`.
- How: the same defaults the `postgres` service already has.
- Why: without a `.env`, Postgres started with `herafi` but backups connected
  with an empty user and failed silently every hour. `docker compose config`
  warned about it.

**T0.2 Pin MinIO images** — done by the job-service owner, PR #10 (`c18f35e`)

- What: `minio` and `minio-init` are built from source by
  `infra/docker/minio/Dockerfile`, pinned to `RELEASE.2025-09-07T16-13-09Z`
  (server) and `RELEASE.2025-08-13T08-35-41Z` (`mc`).
- Why: the move to `quay.io` was deliberate (job-service owner, commit
  `696afae`), and neither `latest` nor the old tags could be pulled there (B1).
  The owner picked the new source, so no DevOps change was needed.

**T0.3 MinIO credentials from env** — commit `727bf20`

- What: `minio`, `minio-init` (`mc alias set`), and job-service `S3_ACCESS_KEY` /
  `S3_SECRET_KEY` read `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`.
- How: `${VAR:-old-value}`, so development behaves exactly as before; both
  variables added to `.env.example`.
- Why: last secret written literally; follows the team's secret pattern.

**T0.4 Postgres healthcheck** — commit `727bf20`

- What: `pg_isready -U $${POSTGRES_USER}`.
- How: `$$` makes Compose leave the variable for the container's shell.
- Why: a custom `DB_USERNAME` made the healthcheck fail, blocking every service.

**T0.5 Gateway healthcheck** — commit `727bf20`

- What: `api-gateway` checks `/actuator/health` like the other services.
- How: same `wget … | grep -q UP` command; `/actuator/health` is already public
  in the gateway's `SecurityConfig`.
- Why: Prometheus (T3) and the demo need to know when the gateway is ready.

**T0.6 README** — commit `7e76bee`

- What: diagram shows application-service (route, REST calls to job and user,
  Eureka, `application_db`); backup and recovery sections say four databases.
- Why: README described the system before application-service existed.

**Verification**

- `docker compose -f infra/docker/docker-compose.yml config -q`: valid, no warnings.
- Isolated project (`-p devops-t0-test`) with `postgres` + `db-backup`: Postgres
  healthy with the new healthcheck; four dumps written; restore test on
  `application_db`: 3 rows → restore → 2 rows (the dump's state). Project removed
  with `down -v`.
- Not run live: the gateway healthcheck. It needs the full stack, which could not
  start before B1 was fixed; it can now, so this check is still to do. The command
  is the same one the other services use.

### 2026-09-28 — Board refresh

- What: commit hashes in this folder updated to the current ones; B1 closed and
  T0.2 marked done after PR #10; T0 checklist in `00-infra-fixes.md` ticked to
  match the log above.
- Why: the old hashes no longer resolve on `main`, and B1 was fixed by the
  job-service owner.
