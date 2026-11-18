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
The projection returns an optional invalidation: duplicates return empty. The Kafka
listener calls the separate transactional service, waits for it to commit, then
sends the invalidation directly. No internal Spring event dispatch is involved.

The two HTTP endpoints have explicit controller methods and typed responses.
`AnalyticsFilter` applies documented defaults and Bean Validation once.
The controller calls the job and application query repositories directly. Each
`summarize` method holds a read-only, repeatable-read transaction and uses its own
SQL with a shared filter clause for both aggregate queries. The total is the sum
of the status distribution. Individual records and pagination are not part of this API.
All persistence uses JDBC; Flyway owns the schema.
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
secret to the service; clients cannot select a different owner. Query parameters:

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | Required ISO-8601 instants; inclusive start and exclusive end, with `from < to`. |
| `granularity` | `day`, `week`, or `month`; defaults to `day`. Weeks begin Monday. |
| `status`, `skill`, `city` | Exact, case-sensitive filters; status must match the appropriate event contract. |

The response has a numeric `total`, and `statusDistribution` and `activity` arrays
of `{value,count}`. It returns chart summaries rather than individual jobs or applications.
All components use the same filters and database snapshot. Activity values are local
date-time bucket starts in `Africa/Casablanca`, ordered chronologically.
A status filter also filters the status distribution.
Invalid query parameters return HTTP 400; Bean Validation failures include field
errors in a Problem Detail response.

For example: `GET /analytics/my/jobs?from=2026-05-01T00:00:00Z&to=2026-06-01T00:00:00Z`.
Missing either date returns HTTP 400; only the granularity defaults to `day`.

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

## Troubleshoot a blocked consumer

An HTTP health check passing does not prove the consumer is processing events or
has caught up. A failed record is retried indefinitely; later records on its
partition wait for it to succeed. Retry errors include the topic, partition,
offset, delivery attempt and exception stack trace, without logging the payload.

1. Read the service logs locally:

   ```sh
   docker compose -f infra/docker/docker-compose.yml logs --tail=200 analytics-service
   ```

   Look for `Analytics event failed; retrying topic=analytics.events partition=... offset=... attempt=...`.
   Repeated failures at the same offset identify the blocked record. Inspect the
   exception for a database outage, schema problem or invalid event contract.
   The current Logstash pipeline does not collect analytics logs; use these
   container logs directly.

2. Check the group's committed offsets and lag with the local Kafka tooling:

   ```sh
   docker compose -f infra/docker/docker-compose.yml exec kafka \
     /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
     --describe --group analytics-projections-v1
   ```

   Compare `CURRENT-OFFSET` and `LOG-END-OFFSET` for each partition. Growing `LAG`
   or an unchanged committed offset during repeated errors indicates that the
   consumer is falling behind. An inactive group also warrants checking startup
   and Kafka connection errors in the service logs.

3. Repair the database/connectivity issue, or fix and redeploy consumer code if it
   cannot read a valid producer contract. Retries resume automatically after a
   transient issue clears. A permanently malformed record requires deliberate
   repair; publishing another event later does not unblock the earlier record.
   Do not skip offsets, delete processed-event receipts or change the group ID as
   a routine fix: that can lose updates or replay an incomplete history.

4. Verify that the retry error stops, committed offsets advance and lag falls.
   Then refetch the affected user's HTTP data to confirm the projection caught up.

## Tests

Tests focus on producer JSON fixtures, HTTP filtering and user isolation, socket
authorization, and transactional projections. Kafka integration cases verify
duplicate delivery, ordering, deletion, application status changes, failure retry
and rollback. Query tests cover required dates and Casablanca time buckets.
Configuration tests check valid startup, a missing JWT secret and a blank gateway
secret instead of repeating the same validation for every setting.
Notification tests check visibility from a separate database connection,
no delivery on transaction commit failure, and continued consumption after socket
delivery failure.
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
