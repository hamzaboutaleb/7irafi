# DevOps Roadmap and Work Log

Every entry records **what** was built, **how**, and **why**, with the commit that
carries it. `docs/` is ignored by git; this folder is committed with `git add -f` at
the lead's request.

Board: [README.md](README.md)

## Roadmap

| Phase | Tasks | Status | Branch |
| --- | --- | --- | --- |
| T0 Infra fixes | T0.1–T0.6 | ✅ Done except T0.2 (blocked, needs job-service owner) | `devops` |
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
| B1 | 2026-09-28 | job-service owner | MinIO images cannot be pulled. `quay.io/minio/minio` and `quay.io/minio/mc` return `unauthorized` / `no such manifest` for anonymous pulls, including `latest` and the old pinned release tags. A fresh machine cannot start the stack. The switch to `quay.io` was the owner's decision (`a718349`), so the new image source is theirs to choose. | Open |
| B2 | 2026-09-28 | lead | `AGENTS.md` says Java 25; all `pom.xml` files use 21 (proposal P4). | Open |

## Log

### 2026-09-28 — T0 Infra fixes

Branch `devops`, from `main` at `883a55e`.

**T0.1 Back up `application_db`** — commit `763a767`

- What: `infra/backup/backup.sh` dumps `application_db` too.
- How: added it to the `DATABASES` list.
- Why: application-service data was never backed up.

**T0.1b `db-backup` credential defaults** (found during T0) — commit `763a767`

- What: `db-backup` uses `${DB_USERNAME:-herafi}` / `${DB_PASSWORD:-herafi}`.
- How: the same defaults the `postgres` service already has.
- Why: without a `.env`, Postgres started with `herafi` but backups connected
  with an empty user and failed silently every hour. `docker compose config`
  warned about it.

**T0.2 Pin MinIO images** — not done, blocked (B1)

- Why: the move to `quay.io` was deliberate (job-service owner, commit
  `a718349`), and neither `latest` nor the old tags can be pulled there, so
  there is no tag to pin until the owner picks a working source.

**T0.3 MinIO credentials from env** — commit `763a767`

- What: `minio`, `minio-init` (`mc alias set`), and job-service `S3_ACCESS_KEY` /
  `S3_SECRET_KEY` read `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`.
- How: `${VAR:-old-value}`, so development behaves exactly as before; both
  variables added to `.env.example`.
- Why: last secret written literally; follows the team's secret pattern.

**T0.4 Postgres healthcheck** — commit `763a767`

- What: `pg_isready -U $${POSTGRES_USER}`.
- How: `$$` makes Compose leave the variable for the container's shell.
- Why: a custom `DB_USERNAME` made the healthcheck fail, blocking every service.

**T0.5 Gateway healthcheck** — commit `763a767`

- What: `api-gateway` checks `/actuator/health` like the other services.
- How: same `wget … | grep -q UP` command; `/actuator/health` is already public
  in the gateway's `SecurityConfig`.
- Why: Prometheus (T3) and the demo need to know when the gateway is ready.

**T0.6 README** — commit `e7648df`

- What: diagram shows application-service (route, REST calls to job and user,
  Eureka, `application_db`); backup and recovery sections say four databases.
- Why: README described the system before application-service existed.

**Verification**

- `docker compose -f infra/docker/docker-compose.yml config -q`: valid, no warnings.
- Isolated project (`-p devops-t0-test`) with `postgres` + `db-backup`: Postgres
  healthy with the new healthcheck; four dumps written; restore test on
  `application_db`: 3 rows → restore → 2 rows (the dump's state). Project removed
  with `down -v`.
- Not run live: the gateway healthcheck (needs the full stack, which can't start
  until B1 is fixed). The command is the same one the other services use.
