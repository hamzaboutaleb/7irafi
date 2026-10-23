# Local Kafka

The Compose stack uses `apache/kafka:4.2.2` as a single combined broker/controller
in KRaft mode. Logs and cluster metadata are stored in the `kafka-data` volume.

From the repository root, start the broker and create its topics:

```sh
docker compose -f infra/docker/docker-compose.yml up -d kafka kafka-init
docker compose -f infra/docker/docker-compose.yml logs kafka-init
```

`kafka-init` waits for broker health and creates both topics with
`--if-not-exists`. It exits after printing their configuration and can be rerun:

```sh
docker compose -f infra/docker/docker-compose.yml run --rm kafka-init
```

| Topic | Purpose | Default partitions | Replicas | Retention |
| --- | --- | --- | --- | --- |
| `analytics.events` | Public job/application analytics events | 3 | 1 | 7 days |
| `jobs.lifecycle` | Internal job snapshots for application-service | 3 | 1 | 7 days |

Topic auto-creation is disabled. Initialization preserves existing topics and their
messages; partition count changes apply when a topic is first created. New topics
inherit Kafka's default seven-day retention, delete cleanup policy, and minimum
in-sync replica count of one. JVM memory also uses the image's defaults. Changes
to existing topics require a deliberate Kafka administration operation.

## Connections

| Client | Bootstrap address |
| --- | --- |
| Services inside Compose | `kafka:9092` |
| Services running directly on the laptop | `localhost:9094` |

---

Job-service and application-service use the Spring Boot Kafka starter and a
`KafkaTemplate`. Spring Boot's default string serializers send event JSON as a
document rather than serializing it again into a quoted JSON string. Producer
acknowledgements, idempotence, and timeouts use Kafka's defaults.

Application-service's consumer configuration uses the `application-service-jobs`
group. A new group starts from the earliest retained events so it can catch up.
Deserializers and listener offset handling use Spring's defaults. Its lifecycle
listener commits the database transaction before the listener returns. The error
handler retries failed messages indefinitely, including invalid payloads, so
records are not discarded after a temporary database outage. An invalid message
blocks its partition until resolved. Processed event IDs and per-job versions
prevent duplicate or stale snapshots from repeating derived transitions.

Service startup does not depend on Kafka health. Topic provisioning belongs to
Compose, and job mutations continue to write their local outbox during a broker
outage.

## Outbox delivery

Both services poll their own committed outbox rows every second and send up to
100 successful deliveries per poll. Each delivery has its own database transaction. PostgreSQL
`FOR UPDATE SKIP LOCKED` lets service instances work on different rows without
claiming the same row concurrently. The row lock stays held while waiting for the
Kafka producer result; Kafka's default metadata and delivery timeouts still apply.
This uses a database connection during a send, including during broker outages.

Messages use the saved destination and JSON payload, with `jobId` as the job-service
key and `applicationId` as the application-service key. A row is marked published
only after Kafka acknowledges it. Failed sends record their error and retry after
1, 2, 4, 8, 16, 32, then at most 60 seconds.
A failure ends the current poll. An older pending version blocks newer versions
of the same entity and destination, including while waiting for its retry; other
entities and destinations remain eligible. Version gaps are allowed.

Delivery is at least once: if Kafka acknowledges a message but the database commit
fails or the process crashes, the same event can be sent again. Retries preserve
the original `eventId` and payload. Consumers must deduplicate by `eventId`;
Kafka producer idempotence alone does not deduplicate an outbox replay.

The publishers are enabled by default. `jobs.outbox.enabled=false` or
`applications.outbox.enabled=false` pauses the respective publisher without stopping
outbox writes. Their corresponding `poll-interval-ms` properties override the default
1000 ms polling delay. `applications.lifecycle.enabled=false` pauses the lifecycle
listener. The one-second poll supports the five-second dashboard
target during normal operation; broker outages and backlogs delay delivery.

## Publisher tests

From `backend/job-service`, `mvn test` covers delivery state, retry backoff,
transaction rollback, ordering, and concurrent publishers with the test database.
To run the same outbox integration tests against PostgreSQL, point Spring's test
datasource at a disposable database and enable Flyway with schema validation:

```sh
mvn -Dtest=JobOutboxIntegrationTests,OutboxEventPublisherIntegrationTests \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/outbox_test \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.datasource.username=herafi -Dspring.datasource.password=herafi \
  -Dspring.flyway.enabled=true -Dspring.jpa.hibernate.ddl-auto=validate test
```

The real Kafka test is opt-in. It checks recovery from an unavailable producer
endpoint and ordered delivery of both JSON contracts using temporary topics that
it deletes afterwards:

```sh
KAFKA_TEST_BOOTSTRAP_SERVERS=localhost:9094 \
  mvn -Dtest=OutboxKafkaIntegrationTests test
```

The PostgreSQL datasource options above can also be used with this Kafka test.
These tests clear the outbox in their test database; use a dedicated test database.

Application-service transaction tests and the disposable-broker Kafka test are
documented in its [README](../../../backend/application-service/README.md#verify).

References: [Apache's Docker examples](https://github.com/apache/kafka/tree/4.2.2/docker/examples),
[Spring Kafka setup](https://docs.spring.io/spring-kafka/reference/quick-tour.html).
