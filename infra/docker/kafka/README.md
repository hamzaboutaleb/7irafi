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
Deserializers and listener offset handling use Spring's defaults. The actual
lifecycle handler, deduplication, and application outbox are subsequent
implementation steps.

Service startup does not depend on Kafka health. Topic provisioning belongs to
Compose, and job mutations continue to write their local outbox during a broker
outage. The scheduled job outbox publisher is also a subsequent implementation
step.

References: [Apache's Docker examples](https://github.com/apache/kafka/tree/4.2.2/docker/examples),
[Spring Kafka setup](https://docs.spring.io/spring-kafka/reference/quick-tour.html).
