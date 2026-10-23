# Application service

Owns job applications and their analytics events. Runs on port `8084`, registers with Eureka
as `application-service`, and owns `application_db` in the shared PostgreSQL
instance. Uses the same Java 21 / Spring Boot stack as job-service.

Included: database migration, entity/repository, health endpoint, gateway-key
protection, `POST /applications`, and `GET /applications/my`. `GET /applications/job/{jobId}` lists applicants for the owner.
`PATCH /applications/{applicationId}/accept` accepts an applicant. There are no cross-database foreign keys to jobs or users.

## Apply to a job

Send an authenticated request through the gateway:

```http
POST /applications
Authorization: Bearer <access-token>
Content-Type: application/json

{"jobId":"3fd67ec9-1aaf-451e-b441-547ce0538d85"}
```

Returns `201` with `id`, `jobId`, `applicantId`, `createdAt`, and `status=PENDING`.
Applicant identity comes from the gateway, never the request body.
Only workers with an `ACTIVE` professional profile in user-service can apply.
Missing, `INCOMPLETE`, or `SUSPENDED` profiles return `403 WORKER_REQUIRED`.
The service checks `GET /internal/users/{userId}/worker-eligibility` using the
internal API key before checking the job or saving. An unavailable user-service
or invalid response returns `503 USER_SERVICE_UNAVAILABLE`; no application is saved.
JWT roles remain `USER`/`ADMIN`: worker eligibility comes from the current profile,
so completing onboarding does not require a refreshed token. This is the MVP
policy in place of the draft job specification's CLIENT/WORKER role promotion.
Existing applications remain readable; this check controls new submissions. The service
checks the internal job summary before saving: missing job → `404`, own job →
`403`, closed job or duplicate application → `409`, job-service unavailable →
`503`, invalid request → `400`. The database enforces uniqueness for concurrent
submissions. Applying does not assign a worker or change the job state.

`PENDING` reflects the open job observed when applying; a job may close concurrently.
HTTP statuses are derived from job-service. A separate stored analytics outcome
tracks which application events have already been emitted.

## Chat context (internal)

`GET /internal/applications/{applicationId}/chat-context` is consumed by the chat
service. It requires the `X-Internal-Api-Key` header and returns the application,
job, applicant, client, and current derived status. `PENDING` applications are
included so the client and applicant can start a conversation before acceptance.

## My applications

`GET /applications/my?page=0&size=10` with a bearer token returns only the current
user's applications. Page starts at zero; size defaults to 10 and is limited to
1–20. Results are ordered by `createdAt DESC, id DESC` and wrapped in `content`,
`page`, `size`, `totalElements`, and `totalPages`.

Status is `PENDING` for open jobs, `ACCEPTED` for the selected worker on assigned
or completed jobs, `NOT_SELECTED` for other applicants, and `CLOSED` for cancelled
or missing jobs. Job-service failures return `503`, not a false closed status.

Deploy the updated user-service for the worker eligibility endpoint.
Deploy the updated job-service as well: its internal summary and assignment
responses include `assignedWorkerId`, `skill`, and `city`. Deploy job-service before
the updated application-service; missing skill/city is treated as an invalid summary.
Application-service applies its new Flyway migrations on startup.

## Job applicants (owner only)

`GET /applications/job/{jobId}?page=0&size=10` with the owner's bearer token
returns the same paginated response as the personal list, newest first. Size
must be 1–20. Each entry includes the application ID, job ID, applicant ID,
submission timestamp, and derived status. A job with no applicants returns an
empty page.

The service fetches the job summary once and checks ownership before querying
applications. Non-owners receive `403`, missing jobs `404`, and job-service
failures `503`. Listing applicants does not change job or application state.

## Accept an applicant (owner only)

`PATCH /applications/{applicationId}/accept` with the owner's bearer token and
no request body returns `200` with the accepted application. Use the application
ID returned by the applicant list, not the job ID or worker ID.

The service checks ownership and asks job-service to perform `OPEN → ASSIGNED`.
It never writes job state directly. Repeating the
request for the selected worker on an assigned or completed job returns success.
Other non-open states and competing assignments return `409`; missing resources
return `404`, non-owners `403`, and job-service failures or timeouts `503`.
After a timeout, retry the same request: assignment may already have succeeded.

## Analytics events

Application-service keeps its own PostgreSQL `outbox_events` table. A new
application retains job skill/city from the internal summary and records
`APPLICATION_SUBMITTED` in the same local transaction. HTTP dependency checks
run before that transaction. Submission metrics and notifications run after commit.

The Kafka listener consumes `jobs.lifecycle` and atomically records the processed
event ID, latest job version, derived analytics outcome, and new outbox events:

- Assignment accepts the selected applicant and rejects other pending applicants.
- Cancellation rejects pending applicants. Completion preserves resolved outcomes.
- Duplicate event IDs and equal/older job versions do not repeat transitions.
- Deletion records the job snapshot without adding a new application transition.

A per-job database lock serializes incoming lifecycle events with new application
inserts. If closure was already processed, an application using a stale open HTTP
summary is rejected. If insertion wins the lock, the later lifecycle handler sees
and resolves the committed application.

Existing applications keep unknown skill/city as null until a lifecycle snapshot
supplies it; the migration preserves their IDs and timestamps. Historical submitted
events are not generated. No database belonging to another service is queried.

The publisher polls every second, using the application ID as the Kafka key and
sending the stored JSON to `analytics.events`. It marks delivery only after a
broker acknowledgement, retries failures with delays capped at 60 seconds, and
blocks later versions of the same application while an older event is pending.
Delivery is at least once: analytics consumers must deduplicate by `eventId`.

`applications.outbox.enabled=true` and `applications.lifecycle.enabled=true` enable
the publisher and listener. Tests disable both unless explicitly checking Kafka.
Listener failures retry indefinitely, keeping failed records pending rather than
discarding them; an invalid message blocks its partition until resolved.

## Run

From the repository root, for a fresh PostgreSQL volume:

```sh
docker compose -f infra/docker/docker-compose.yml up --build -d user-service application-service api-gateway
```

If PostgreSQL already has data, first start PostgreSQL and rerun the idempotent
database initialization script. PostgreSQL only runs init scripts automatically
when its data directory is empty. These commands preserve existing databases:

```sh
docker compose -f infra/docker/docker-compose.yml up -d postgres
docker compose -f infra/docker/docker-compose.yml exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-create-databases.sql'
docker compose -f infra/docker/docker-compose.yml up --build -d user-service application-service api-gateway
```

Check health inside the container (the service port is not published):

```sh
docker compose -f infra/docker/docker-compose.yml exec application-service wget -qO- http://localhost:8084/actuator/health
```

For local development with Java 21 and Maven, create `application_db`, then run
`mvn spring-boot:run` from this directory. Configuration accepts `SERVER_PORT`,
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DISCOVERY_SERVICE_URL`,
`GATEWAY_SHARED_SECRET`, `INTERNAL_API_KEY`, `JOB_SERVICE_URL`, and
`USER_SERVICE_URL` (default `http://localhost:8082`). The job HTTP client uses the internal API key and job-service URL settings.
Defaults are for development only.

## Verify

```sh
mvn test
```

Regular tests use H2 and cover outbox transactions, derived transitions, duplicate
and stale lifecycle events, retries, ordering, and concurrent submissions/closures.
The internal HTTP client tests verify skill/city response handling. They do not
require a running PostgreSQL, Kafka, user-service, or Eureka instance.

Run against a disposable PostgreSQL database to validate Flyway and real row locks:

```sh
mvn -Dspring.datasource.url=jdbc:postgresql://localhost:5432/application_events_test \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.datasource.username=herafi -Dspring.datasource.password=herafi \
  -Dspring.flyway.enabled=true -Dspring.jpa.hibernate.ddl-auto=validate test
```

The optional Kafka test starts the actual listener and scheduled publisher, tests
assignment and cancellation, replays duplicate/stale events, and retries a failed
database write. Point it at a disposable broker: it creates and writes synthetic
messages to `jobs.lifecycle` and `analytics.events`.

```sh
APPLICATION_KAFKA_TEST_BOOTSTRAP_SERVERS=localhost:19094 \
  mvn -Dtest=ApplicationKafkaIntegrationTests test
```

The PostgreSQL options above can be added to this command. Integration tests clear
application/outbox data in their test database; use a dedicated test database.
