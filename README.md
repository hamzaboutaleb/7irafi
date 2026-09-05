# 7irafi

7irafi is a services marketplace that connects clients with local professionals. The
application is built as an Angular frontend and a Spring Boot backend split by business
responsibility.

## Run locally

Secrets are not committed. Create your local `.env` from the template first — the stack
will not start without it:

```bash
cp infra/docker/.env.example infra/docker/.env
# then edit infra/docker/.env and set a value for each variable
```

```bash
docker compose -f infra/docker/docker-compose.yml up --build
```

The frontend is available at `http://localhost:4200`; browser API traffic goes through
the API Gateway at `http://localhost:8080`. The Eureka dashboard is available for local
development at `http://localhost:8761`.

## Backend architecture

```mermaid
flowchart LR
    B[Browser] --> F[Angular frontend]
    F --> G[API Gateway]
    G -->|lb://AUTH-SERVICE| A[Auth Service]◊
    G -->|lb://USER-SERVICE| U[User Service]
    G -->|lb://JOB-SERVICE| J[Job Service]
    A -->|load-balanced REST| U
    G -. registers / discovers .-> E[Eureka]
    A -. registers / discovers .-> E
    U -. registers / discovers .-> E
    J -. registers / discovers .-> E
    A --> AD[(auth_db)]
    U --> UD[(user_db)]
    J --> JD[(job_db)]
```

The gateway is the public backend entry point. It validates JWTs for protected routes,
removes client-supplied trusted headers, and forwards requests by logical service name.
Auth owns credentials and token issuance; User owns profiles; Job owns job listings.
Each data-owning service owns its Flyway-managed schema and does not query another
service's tables.

Development uses one PostgreSQL container with separate logical databases. That is a
local-resource decision, not a claim that each service already has an independently
operated database server.

## Claimed module: Backend as microservices — Major (2 points)

This project is claiming the subject's **Backend as microservices** Major. The module
requires loosely coupled services with clear interfaces, REST APIs or message queues for
communication, and a single responsibility for each service.

| Requirement | Evidence in 7irafi |
| --- | --- |
| Focused responsibilities | Auth handles credentials/tokens, User handles profiles, Job handles jobs, Gateway handles the edge boundary, and Eureka handles discovery. |
| Clear interfaces | The gateway exposes `/auth/**`, `/users/**`, and `/jobs/**`; Auth creates profiles through User's internal REST endpoint. |
| Loose location coupling | Gateway routes use `lb("…-SERVICE")`; Auth calls `http://USER-SERVICE` through a `@LoadBalanced RestClient.Builder`, not a container address. |
| Independent data ownership | `auth_db`, `user_db`, and `job_db` have separate Flyway migrations. No cross-service table access is used. |
| Runtime discovery and load balancing | Gateway, Auth, User, and Job register in Eureka. User Service can run as multiple replicas and is selected by Spring Cloud LoadBalancer. |

Kafka is deliberately **not** part of this claim: the subject accepts REST *or* message
queues, and the implemented registration flow is REST-based. Kafka remains a later
learning milestone, not a prerequisite for these two points.

The claim still has to be demonstrated live to an evaluator; documentation alone never
earns module points. Follow the [microservices evaluation guide](docs/architecture/loosely-coupled-microservices-major.md)
for the exact evidence and demo sequence. The supporting architecture notes cover
[service discovery and load balancing](docs/architecture/service-discovery.md), the
[gateway boundary](docs/architecture/api-gateway.md), [Auth Service](docs/architecture/auth-service.md),
[User Service](docs/architecture/user-service.md), and [database ownership](docs/architecture/postgresql.md).

## Current boundaries and follow-up work

- The Auth → User registration call is synchronous. It has no explicit timeout,
  retry policy, or idempotency workflow yet.
- Job Service is registered and routed through Eureka, but its trusted-caller filter is
  still outstanding. Its development port must not be treated as a public production
  interface until that boundary is implemented.
- Service databases are logically separated locally; production should give each
  service least-privilege credentials and, when justified, separate database instances.
- TLS, secret rotation, observability, and failure-recovery demonstrations remain
  project-wide completion work.

## Kafka foundation

Local development now includes a single-node KRaft Kafka broker with separate internal
and host listeners. No application service produces or consumes events yet; the first
useful integration is Job lifecycle events consumed by the future Notification Service.
See the [Kafka foundation and event contract](docs/architecture/kafka.md).

## Service status

A public status page is served at `http://localhost:4200/status`, backed by
`GET /status` on the API Gateway.

Services are discovered from Eureka rather than hardcoded: a service added later appears
automatically, and one that has crashed and deregistered disappears. Each discovered
service is probed on its `/actuator/health` with a 2-second timeout, so a hung service is
reported as `DOWN` instead of hanging the page. Overall status is `UP` only when every
service reports `UP`, otherwise `DEGRADED`.

The page and the endpoint are both unauthenticated by design — a status page that cannot
be reached during an outage is not a status page.

## Backups

`db-backup` runs alongside the stack and dumps every service database (`auth_db`,
`user_db`, `job_db`) on an interval, into the `postgres-backups` volume. Databases are
dumped separately because each service owns its own schema and must be restorable
independently.

| Variable | Default | Meaning |
| --- | --- | --- |
| `BACKUP_INTERVAL_SECONDS` | `3600` | How often a backup runs |
| `BACKUP_RETENTION_DAYS` | `7` | Dumps older than this are pruned |

A backup is taken immediately on start, so a fresh stack has one within seconds. Retention
matters: without pruning, the volume grows until the disk fills, which turns a backup
system into an outage.

List the dumps:

```bash
docker compose -f infra/docker/docker-compose.yml exec db-backup ls -lh /backups
```

## Disaster recovery

Restoring is deliberately manual — it overwrites live data, so it is always a human
decision.

**1. Identify the dump to restore from.**

```bash
docker compose -f infra/docker/docker-compose.yml exec db-backup ls -lh /backups
```

Dumps are named `<database>_<UTC timestamp>.sql.gz`.

**2. Restore that database.**

```bash
docker compose -f infra/docker/docker-compose.yml exec db-backup \
    /scripts/restore.sh user_db /backups/user_db_20260905T133550Z.sql.gz
```

Dumps are taken with `--clean --if-exists`, so the restore drops and recreates each object
as it goes; no manual cleanup is needed. `psql` runs with `ON_ERROR_STOP=on` so it aborts
on the first failure instead of leaving a half-restored database.

**3. Restart the owning service** so it reconnects and re-validates its schema:

```bash
docker compose -f infra/docker/docker-compose.yml restart user-service
```

**Verified end to end on 2026-09-05:** 10 rows were deleted from `professional_profiles`
(45 -> 35), the latest dump was restored, and the count returned to 45. An untested restore
procedure is not a recovery procedure.

### If the database volume is lost entirely

`docker volume rm docker_postgres-data` (or a disk failure) destroys all three databases.
Recovery:

1. `docker compose -f infra/docker/docker-compose.yml up -d postgres` — the volume is
   recreated and `infra/docker/postgres/init/01-create-databases.sql` recreates the three
   empty databases.
2. Restore each database from its most recent dump, as above.
3. Start the remaining services. Flyway will find the schema already at the dumped version
   and apply nothing.

Note that `postgres-backups` is a separate volume from `postgres-data`, so losing the
database volume does not lose the dumps. On a real deployment they would live off-host.

