# The same analytics operations with JDBC and JPA

This PR runs Java 21, Spring JDBC and Flyway. JPA is an alternative, not a second
implementation enabled by a profile. The JPA snippets below are a design comparison;
they are not compiled or covered by this PR's tests. They show the job path; the
application path would need the corresponding entity and repository too.

JPA is the persistence API, Hibernate would be the ORM implementation, and Spring
Data JPA supplies repository implementations. Flyway continues to manage the schema
in either version. Changing to JPA does not change Kafka events or HTTP responses.

## 1. Saving an incoming snapshot

The current [ProjectionRepository](../src/main/java/com/herafi/analytics/repository/ProjectionRepository.java)
sends one PostgreSQL upsert with bound parameters:

```java
jdbc.update("""
        INSERT INTO analytics_jobs(job_id, client_id, status, skill, city, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (job_id) DO UPDATE SET
            client_id = EXCLUDED.client_id,
            status = EXCLUDED.status,
            skill = EXCLUDED.skill,
            city = EXCLUDED.city,
            created_at = EXCLUDED.created_at
        """, job.jobId(), job.clientId(), job.status().name(), job.skill(), job.city(),
        Timestamp.from(job.createdAt()));
```

With JPA, introduce an entity. UUIDs reference producer-owned records; they should
remain scalar IDs, without cross-service entity associations. DTOs can remain records;
the entity uses a class with a no-argument constructor and field access:

```java
@Entity
@Table(name = "analytics_jobs")
public class AnalyticsJob {
    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private JobStatus status;

    @Column(name = "skill", length = 100)
    private String skill;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AnalyticsJob() { }

    public AnalyticsJob(JobEvent.Data snapshot) {
        jobId = snapshot.jobId();
        apply(snapshot);
    }

    public void apply(JobEvent.Data snapshot) {
        clientId = snapshot.clientId();
        status = snapshot.status();
        skill = snapshot.skill();
        city = snapshot.city();
        createdAt = snapshot.createdAt();
    }
}

public interface JobRepository extends JpaRepository<AnalyticsJob, UUID> { }
```

Then the write could be:

```java
@Transactional
public void saveJob(JobEvent.Data snapshot) {
    jobs.save(new AnalyticsJob(snapshot));
}
```

The ORM handles parameter binding, enum/timestamp conversion and generated writes.
For these assigned, non-null UUIDs without a version field, Spring Data's default
newness detection selects `merge`. That is not PostgreSQL `ON CONFLICT`: Hibernate
may read before inserting/updating, and SQL may execute at flush/commit. `save` also
returns the managed instance; the argument is not necessarily managed.
[Spring Data entity persistence documentation](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html).

For an already-loaded managed entity, `entity.apply(snapshot)` inside a transaction
is enough: dirty checking writes changes at flush. This is useful when working with
entity state; our input is already a complete snapshot, so loading it first adds a read.

## 2. Querying chart summaries

Today [JobAnalyticsRepository](../src/main/java/com/herafi/analytics/repository/JobAnalyticsRepository.java)
shares one SQL filter between the distribution and activity queries,
derives the total from the distribution, then maps aggregate rows to count records.
Individual records and pagination are outside the chart API.

JPA can express status grouping in JPQL and return an interface projection.
The same predicates are still required:

```java
public interface JobRepository extends JpaRepository<AnalyticsJob, UUID> {
    @Query("""
            select j.status as status, count(j) as count from AnalyticsJob j
            where j.clientId = :owner
              and j.createdAt >= :from and j.createdAt < :to
              and (:status is null or j.status = :status)
              and (:skill is null or j.skill = :skill)
              and (:city is null or j.city = :city)
            group by j.status order by j.status
            """)
    List<StatusCount> countByStatus(
            @Param("owner") UUID owner,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") JobStatus status,
            @Param("skill") String skill,
            @Param("city") String city);

    interface StatusCount {
        JobStatus getStatus();
        long getCount();
    }
}
```

This replaces the JDBC aggregate row mapper with a projection. The total can still
be calculated by summing the returned counts. Dynamic filters can use
Specifications, but sharing predicates with grouped queries requires additional
Criteria code. An ORM does not automatically keep those filters consistent.
[Spring Data query documentation](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).

The Casablanca `date_trunc(... AT TIME ZONE ...)` expression remains most directly
expressed as a PostgreSQL native query in a JPA repository. A migration could keep
that query while removing manual row mapping via a projection.

## 3. Deduplication and transactions

The current event receipt uses `INSERT ... ON CONFLICT DO NOTHING`, whose affected
row count decides whether to apply the snapshot. It runs in the same transaction
as the snapshot and before the after-commit notification.

JPA can run that exact statement with `@Modifying` and `@Query(nativeQuery = true)`
on a repository for a mapped `ProcessedEvent` entity:

```java
@Modifying
@Query(value = """
        INSERT INTO analytics_processed_events(event_id)
        VALUES (:eventId) ON CONFLICT DO NOTHING
        """, nativeQuery = true)
int recordEvent(@Param("eventId") UUID eventId);
```

Keep the outer `ProjectionService.apply` transaction. An `existsById` followed by
`save` is not an equivalent atomic deduplication operation; catching a unique-key
failure inside the transaction can leave it unable to commit. A JPA `@Version`
field would track local entity updates, not a producer event version or Kafka order.

## What would change in a JPA implementation?

| Concern | Current JDBC version | JPA version |
| --- | --- | --- |
| Dependencies | JDBC starter | Data JPA starter, Hibernate supplied by Boot |
| Schema | Flyway migrations | Same migrations; `ddl-auto=validate`, never `update` |
| Configuration | No persistence context | Disable open-in-view; preserve transaction boundaries |
| Snapshot writes | Explicit atomic upsert | Entity `save`/dirty checking, or retain native upsert |
| Status counts | SQL plus row mapper | JPQL plus aggregate projection |
| Time buckets | PostgreSQL SQL | Native query or provider-specific functions |
| Deduplication | Atomic native insert | Same native insert through a JPA repository |
| Tests | Current PostgreSQL/Kafka suite | Rerun unchanged contracts; test flush-time failures too |

JPA saves mapping and CRUD code and matches the producer services' conventions.
JDBC keeps the database operations explicit and compact for this read model. A JPA
version is viable; it would be a separate implementation change, not merely swapping
the dependency. Both versions should retain filtering, transaction and replay tests.
