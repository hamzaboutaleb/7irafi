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
