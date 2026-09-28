# Proposals — Need the Lead's Approval

Back to [task board](README.md).

These are not part of the lead's DevOps request. They are DevOps-scope improvements
found while reading the project. Raise them with the lead; start only the approved
ones, and move them to the task board when approved.

## P1 — CI pipeline

There is no `.github/workflows` directory.

Scope if approved:

- Backend workflow: matrix over the 6 services, Java 21, `mvn -B verify` on pull
  requests.
- Frontend workflow: `npm ci`, `npm test`, `npm run build`.
- Docker build check for each Dockerfile (no push).
- `docker compose config -q` and `promtool check` for the Prometheus files.
- Optional: gitleaks secret scanning.

Why: the evaluation-day demo depends on `main` always building.

## P2 — Multi-stage, non-root Dockerfiles

Every backend image is `maven:3.9.12-eclipse-temurin-21-alpine` running as root:
it ships Maven and the full JDK.

Scope if approved: build stage with Maven, runtime stage
`eclipse-temurin:21-jre-alpine` with a non-root user. Same ports, same
entrypoint behaviour. job-service keeps its current development setup unless its
owner agrees to change it.

Why: smaller images and less memory, which matters once ELK is added to the stack.

## P3 — Compose profiles

After T3–T6 the stack includes Prometheus, Grafana, Alertmanager, node-exporter,
Elasticsearch, Logstash, and Kibana (about 3–4 GB extra).

Scope if approved: put them under `profiles: [monitoring]` and `profiles: [logging]`.
The evaluation command becomes `docker compose --profile monitoring --profile
logging up`; developers can skip them.

Conflict to raise: the lead asked for "one Compose command" for the demo. Profiles
still give one command, but the lead should confirm.

## P4 — Java version in `AGENTS.md`

`AGENTS.md` says Java 25; every `pom.xml` and Dockerfile uses 21. DevOps follows the
code (21). Ask the lead whether `AGENTS.md` should be corrected.

## Observations for service owners (not DevOps work)

Share these; the owners decide.

- job-service runs `mvn spring-boot:run` with `src/` mounted (commit `0375d4c`,
  "configure Docker for development"). It starts slower than the other services.
- Postgres `5432`, MinIO `9000`/`9001`, Eureka `8761`, and job-service `8083` are
  published on all interfaces. T1.2 binds Eureka and job-service to `127.0.0.1`
  for metrics privacy; developers keep local access.
- `README.md` says the stack will not start without `.env`, but Compose has dev
  defaults for every variable (commit `e4b38db`).
