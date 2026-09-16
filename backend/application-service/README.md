# Application service

Initial scaffold for job applications. Runs on port `8084`, registers with Eureka
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
No application status is stored. The personal list derives current status from job-service.

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
responses now include nullable `assignedWorkerId`. No database migration is needed.

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
It never writes job state or stores a separate acceptance status. Repeating the
request for the selected worker on an assigned or completed job returns success.
Other non-open states and competing assignments return `409`; missing resources
return `404`, non-owners `403`, and job-service failures or timeouts `503`.
After a timeout, retry the same request: assignment may already have succeeded.

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

Tests run the Flyway migration against H2 in PostgreSQL mode, check duplicate
applicants, and verify gateway-key protection and health-path access. Worker eligibility tests also cover active/ineligible applicants, upstream errors,
invalid responses, and connection failures. They do not require a running
PostgreSQL, user-service, or Eureka instance.
