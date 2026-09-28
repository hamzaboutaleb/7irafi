# DevOps Task Board

Last updated: September 28, 2026

Owner: DevOps. This board tracks the DevOps work for 7irafi. Heavy tasks link to
their own file with implementation notes and acceptance criteria.

The board has three parts, and only the first two are committed work:

1. **Requested by the lead** (T1–T8): the Monitoring and ELK modules.
2. **Defects in DevOps-owned files** (T0): bugs in infra DevOps already owns.
3. **Proposals** (P1–P4): not requested. Get the lead's approval before starting.

Rule: DevOps changes infra files (`infra/`, Compose, Dockerfiles, `pom.xml`
dependencies, `application.properties` observability settings, filters for logs and
metrics). Choices already made by service owners, such as how job-service runs in
Compose or which host ports are published, are respected and not changed without
their agreement.

## Current state

| Area | Status |
| --- | --- |
| Containerisation (6 services + frontend) | Done |
| Single Compose stack with healthchecks | Done |
| Secrets through env variables with dev defaults | Done |
| Automated backups + restore procedure | Done (all 4 databases, restore tested) |
| Public status page (`/status`) | Done |
| Metrics (Micrometer / Prometheus / Grafana) | Not started |
| Alerting | Not started |
| Structured logging / correlation IDs | Not started (`X-Request-Id` is only passed through) |
| ELK + retention | Not started |

## Decisions

Decisions the lead's list leaves to DevOps, and facts taken from the code. Update
this table if the lead overrides one.

| Topic | Decision | Source |
| --- | --- | --- |
| Java version for images and CI | **21** | Every `pom.xml` sets `java.version` 21 (`AGENTS.md` says 25; the code wins) |
| Spring Boot version | 4.1.x | `pom.xml` files |
| Log format | Spring Boot built-in structured logging (`logstash` format) to stdout | No new dependency; one property per service |
| Log input for Logstash | **Docker JSON logs** (Logstash `file` input on the Docker log files) | Lead allowed Docker logs or TCP. Docker logs need no service code or appender. |
| Alert delivery | **Alertmanager** | Lead allowed Alertmanager or Grafana alerts; `alerts.yml` is in the required file list |
| Log retention | Dev 7 days, prod 30 days | Lead asked DevOps to define both |
| Log archiving | Not archived; production option documented | Lead asked to document whether logs are archived |
| Secrets for new tools | `${VAR:-dev-default}` in Compose, real values in `.env` | Same pattern the team uses for `JWT_SECRET`, `DB_PASSWORD` |
| Metrics privacy | Actuator stays on each service's main port (the status page probes `/actuator/health` there); gateway requires scrape credentials; other published ports bound to `127.0.0.1` | See T1.2 |

Progress log: [ROADMAP.md](ROADMAP.md)

## T0 — Defects in DevOps-owned files → [00-infra-fixes.md](00-infra-fixes.md)

- [x] T0.1 Back up `application_db`
- [x] T0.2 Pin MinIO images — done by the job-service owner in PR #10 (built from source at pinned release tags)
- [x] T0.3 Read MinIO credentials from env variables
- [x] T0.4 Make the Postgres healthcheck use the configured user
- [x] T0.5 Add a healthcheck to `api-gateway`
- [x] T0.6 Update the README diagram and backup docs

## Monitoring module (Major, 2 points)

### T1 — Metrics in every service → [01-metrics-instrumentation.md](01-metrics-instrumentation.md)

- [ ] T1.1 Add `micrometer-registry-prometheus` to all 6 services
- [ ] T1.2 Expose `/actuator/prometheus`, private to the Docker network
- [ ] T1.3 Common tags: `application`, `environment`, `instance`
- [ ] T1.4 HTTP latency histograms and percentiles
- [ ] T1.5 Verify JVM and HikariCP metrics
- [ ] T1.6 Business counters (🤝 agree placement with each service owner)
- [ ] T1.7 Tests

### T3 — Prometheus → [03-prometheus.md](03-prometheus.md)

- [ ] T3.1 Compose service with volume and retention
- [ ] T3.2 `prometheus.yml` scrape config
- [ ] T3.3 Verify all targets are UP

### T4 — Grafana → [04-grafana.md](04-grafana.md)

- [ ] T4.1 Compose service, volume, configured admin password
- [ ] T4.2 Provisioned Prometheus data source
- [ ] T4.3 Dashboard provider provisioning
- [ ] T4.4 `7irafi-overview.json` dashboard (7 panel groups)

### T5 — Alerting → [05-alerting.md](05-alerting.md)

- [ ] T5.1 `alerts.yml` (6 rules)
- [ ] T5.2 node-exporter for the disk pressure rule
- [ ] T5.3 Alertmanager
- [ ] T5.4 Rehearse the service-down alert

## ELK module (Major, 2 points)

### T2 — Correlation IDs and JSON logging → [02-request-id-and-json-logging.md](02-request-id-and-json-logging.md)

- [ ] T2.1 Gateway request-ID filter
- [ ] T2.2 MDC filter in every service
- [ ] T2.3 Forward the ID on internal REST calls
- [ ] T2.4 JSON log format in all 6 services
- [ ] T2.5 Access-log filter (path, method, status, duration)
- [ ] T2.6 Sensitive data never logged
- [ ] T2.7 Business event logs (🤝 agree placement with each service owner)
- [ ] T2.8 Tests and end-to-end trace check

### T6 — ELK stack → [06-elk-stack.md](06-elk-stack.md)

- [ ] T6.1 Elasticsearch
- [ ] T6.2 Logstash pipeline (Docker JSON logs input)
- [ ] T6.3 Kibana
- [ ] T6.4 Data view and dashboards
- [ ] T6.5 Export and auto-import Kibana objects

### T7 — Log retention (ILM) → [07-log-retention-ilm.md](07-log-retention-ilm.md)

- [ ] T7.1 ILM policy and index template
- [ ] T7.2 Dev and prod retention periods
- [ ] T7.3 Document the archiving decision
- [ ] T7.4 Verify deletion

## Both modules

### T8 — README, demo script, and rehearsal → [08-readme-and-demo.md](08-readme-and-demo.md)

- [ ] T8.1 README Monitoring section
- [ ] T8.2 README ELK section
- [ ] T8.3 `.env.example` complete
- [ ] T8.4 Traffic and error demo script
- [ ] T8.5 Evaluation demo checklist
- [ ] T8.6 Clean-clone dry run and memory check

Recommended order (from the lead): T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8. T0 can
go first as a small separate PR.

## Ongoing DevOps responsibilities

- [ ] When a service or database is added, update Compose,
  `infra/docker/postgres/init/01-create-databases.sql`, `infra/backup/backup.sh`,
  and `infra/docker/prometheus/prometheus.yml`.
- [ ] Keep `.env.example` in sync with every new variable.
- [ ] One PR per task, listing affected services, config/API changes, and commands
  run (see `AGENTS.md`).
- [ ] Re-run the restore procedure after any database or backup change.

## Proposals — need the lead's approval → [proposals.md](proposals.md)

Not part of the lead's request. Do not start these without approval.

- P1 CI pipeline (GitHub Actions)
- P2 Multi-stage, non-root Dockerfiles
- P3 Compose `profiles` so developers can skip the monitoring/logging containers
- P4 `AGENTS.md` says Java 25 but the code uses 21: ask the lead which is correct

## Module scoring reminder

Neither module can be claimed with containers alone: the evaluator must see working
metrics, dashboards, alerts, searchable logs, retention, and a live demonstration
(T8).
