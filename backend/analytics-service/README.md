# Analytics service

Analytics consumes the public job and application snapshots from `analytics.events`
into its own PostgreSQL database. It does not read producer databases. Kafka keys
preserve the producer's ordering for each entity, and event IDs are recorded in the
same transaction as each projection. Failures retry indefinitely and block the
affected partition until corrected. Job deletion removes the job from job results;
application records follow application events independently.

The service uses Java 21 records, sealed event types and exhaustive switches.
Jackson selects a typed job or application event from the existing `eventType`
field. Record constructors validate that contract before `ProjectionService`
records the event receipt and calls the appropriate repository operation.
An invalidation is published after the database transaction commits.

The two HTTP endpoints have explicit controller methods and typed responses.
`AnalyticsFilter` applies documented defaults and Bean Validation once.
`AnalyticsQueries` holds the read transaction, while the job and application
repositories each contain their own SQL with a shared filter clause for all four
queries. All persistence uses JDBC; Flyway owns the schema.
See [JDBC and JPA compared](docs/jdbc-vs-jpa.md) for concrete examples of how the
same projection and queries would look with an ORM.

The consumer group `analytics-projections-v1` starts at the earliest **retained**
offset. The local topic retains seven days by default; this is not a historical
backfill. Existing applications may have no submission event. To rebuild, provision
complete event history separately; changing offsets alone cannot recover expired events.
Do not reset the consumer group without also understanding the projection and deduplication
state. The processed-event table must be retained for as long as old events can be replayed.

## Run locally

Copy `infra/docker/.env.example` to `infra/docker/.env` if that file does not exist,
then configure the credentials for your environment. Compose retains the existing
development defaults; changing the stack's startup policy is outside this service's scope.
Generate secrets locally (for example, `openssl rand -hex 32`); do not commit `.env`.
Existing database volumes keep their original password: configure that password
or deliberately rotate it in PostgreSQL before changing the client setting.

For an existing PostgreSQL volume, run the idempotent database setup first:

```sh
docker compose -f infra/docker/docker-compose.yml up -d postgres
docker compose -f infra/docker/docker-compose.yml exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/01-create-databases.sql'
docker compose -f infra/docker/docker-compose.yml up --build -d analytics-service api-gateway notification-service
```

New volumes initialize the database automatically. Wait for discovery to propagate
the services after health checks pass before querying analytics. Kafka topics are provisioned by
`kafka-init`. The gateway exposes HTTP on port 8080; the analytics socket is exposed
directly on port 8087, matching the existing chat and notification socket pattern.

When running the Java process outside Compose, explicitly set `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `DISCOVERY_SERVICE_URL`,
`GATEWAY_SHARED_SECRET`, `JWT_SECRET`, `JWT_ISSUER`, and `FRONTEND_ORIGIN`.
For local infrastructure these URLs normally use localhost, Kafka uses port 9094,
and the issuer must match auth-service (`7irafi-auth-service`). Analytics does not
load the Compose `.env` automatically. Missing or blank required configuration
fails startup through validated `@ConfigurationProperties` records. Unset environment
variables bind to empty values so validation rejects them, rather than accepting
unresolved placeholder strings. No usable credential or connection default is supplied
by the Java service. JWT keys must contain at least 32 bytes. Only the server port has
a runtime default (8087). The test profile supplies its own explicit settings.

## HTTP API

Use a bearer token at the gateway for `GET /analytics/my/jobs` or
`GET /analytics/my/applications`. The gateway supplies the user identity and a shared
secret to the service; clients cannot select a different owner. Optional parameters:

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | ISO-8601 instants; inclusive start and exclusive end. Defaults: 1970-01-01 and 9999-01-01 UTC. |
| `granularity` | `day`, `week`, or `month`; defaults to `day`. Weeks begin Monday. |
| `status`, `skill`, `city` | Exact, case-sensitive filters; status must match the appropriate event contract. |
| `page`, `size` | Zero-based page; default 0/10, size 1–20. |

The response has a numeric `total`, `statusDistribution` and `activity` arrays of
`{value,count}`, plus `records`, `page` and `size`. Typed records preserve the
existing snake_case field names and expose timestamps as ISO-8601 instants.
All components use the same filters and database snapshot. Activity values are local
date-time bucket starts in `Africa/Casablanca`; records are newest first with an ID
tiebreaker. A status filter also filters the status distribution.
Invalid query parameters return HTTP 400; Bean Validation failures include field
errors in a Problem Detail response.

## Live invalidations

Connect to `/ws/analytics` with STOMP and send `Authorization: Bearer <token>` in
the CONNECT frame. Subscribe to `/user/queue/analytics`. Only that subscription is
allowed, and messages contain `eventId`, `resource` (`jobs` or `applications`), and
`occurredAt`. Fetch HTTP data initially, after relevant invalidations, and on reconnect.
Notifications are best effort after commit; HTTP is authoritative. This local setup
assumes one analytics instance: multiple instances require shared message delivery or
fan-out to reach sockets connected elsewhere. The five-second target is conditional on
healthy publishers, Kafka, the consumer and a manageable backlog.
Messaging failures are logged after commit and do not undo the projection. Database
and event-contract failures are retried without advancing past the failed record;
repair the underlying problem before expecting that partition to catch up.

## Tests

Tests cover producer JSON contracts, HTTP filters and validation, socket
authorization, and transactional projections. Kafka integration cases verify
duplicate delivery, ordering, deletion, transient database failure, rollback,
and listener restart. Configuration tests reject every missing or blank deployment
setting. Notification tests check visibility from a separate database connection
and continued consumption after socket delivery failure.
Each case uses unique users and event IDs without clearing
other tests' rows.

Run with Java 21 and a dedicated PostgreSQL database. The test profile defaults to
`localhost:55439/analytics_test` with username/password `herafi`:

```sh
docker run --rm -d --name analytics-test-db -p 127.0.0.1:55439:5432 \
  -e POSTGRES_USER=herafi -e POSTGRES_PASSWORD=herafi -e POSTGRES_DB=analytics_test postgres:17-alpine
cd backend/analytics-service
mvn clean test
docker stop analytics-test-db
```

Use `ANALYTICS_TEST_DB_URL`, `ANALYTICS_TEST_DB_USERNAME` and
`ANALYTICS_TEST_DB_PASSWORD` to target another disposable database.
