package com.herafi.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.OutboxEvent;
import com.herafi.job_service.event.JobAnalyticsEvent;
import com.herafi.job_service.event.JobLifecycleEvent;
import com.herafi.job_service.repository.OutboxEventRepository;

import tools.jackson.databind.json.JsonMapper;

/** Opt in with KAFKA_TEST_BOOTSTRAP_SERVERS; creates and removes its own topics. */
@EnabledIfEnvironmentVariable(named = "KAFKA_TEST_BOOTSTRAP_SERVERS", matches = ".+")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(OutboxEventDispatcher.class)
@ImportAutoConfiguration(KafkaAutoConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxKafkaIntegrationTests {

    @Autowired private OutboxEventDispatcher dispatcher;
    @Autowired private OutboxEventRepository events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getenv("KAFKA_TEST_BOOTSTRAP_SERVERS"));
    }

    @Test
    void recoversFromAnUnavailableBrokerAndDeliversBothContractsInOrder() throws Exception {
        String broker = System.getenv("KAFKA_TEST_BOOTSTRAP_SERVERS");
        String prefix = "outbox-test-" + UUID.randomUUID();
        List<String> topics = List.of(prefix + "-analytics", prefix + "-lifecycle");
        UUID jobId = UUID.randomUUID();
        events.deleteAllInBatch();

        try (Admin admin = Admin.create(Map.of("bootstrap.servers", broker))) {
            admin.createTopics(topics.stream().map(topic -> new NewTopic(topic, 3, (short) 1)).toList())
                    .all().get(15, TimeUnit.SECONDS);
            try {
                saveSnapshots(jobId, topics);
                OutboxEvent first = events.findAll().stream()
                        .filter(event -> event.getDestination().equals(topics.getFirst())
                                && event.getJobVersion() == 0).findFirst().orElseThrow();
                jdbc.update("UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?",
                        Timestamp.from(Instant.EPOCH), first.getId());

                // Keep the real broker running: only this producer points at an unavailable port.
                var unavailableFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                        "bootstrap.servers", "127.0.0.1:1", "max.block.ms", 1000,
                        "request.timeout.ms", 500, "delivery.timeout.ms", 1000),
                        new StringSerializer(), new StringSerializer());
                var unavailableKafka = new KafkaTemplate<>(unavailableFactory);
                try {
                    var unavailableDispatcher = new OutboxEventDispatcher(events, unavailableKafka);
                    Boolean delivered = new TransactionTemplate(transactionManager).execute(
                            transaction -> unavailableDispatcher.publishNext());
                    assertThat(delivered).isFalse();
                } finally {
                    unavailableKafka.destroy();
                    unavailableFactory.destroy();
                }
                assertThat(events.findById(first.getId()).orElseThrow().getPublishedAt()).isNull();

                // Lifecycle delivery can progress while analytics waits; then make its retry due.
                assertThat(dispatcher.publishNext()).isTrue();
                jdbc.update("UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?",
                        Timestamp.from(Instant.EPOCH), first.getId());
                for (int remaining = 0; remaining < 5; remaining++) {
                    assertThat(dispatcher.publishNext()).isTrue();
                }
                assertThat(dispatcher.publishNext()).isFalse();
                List<OutboxEvent> snapshots = events.findAll();
                assertThat(snapshots).allSatisfy(event -> assertThat(event.getPublishedAt()).isNotNull());
                assertThat(events.findById(first.getId()).orElseThrow().getAttempts()).isEqualTo(2);

                try (var consumer = new KafkaConsumer<String, String>(Map.of(
                        "bootstrap.servers", broker, "enable.auto.commit", false),
                        new StringDeserializer(), new StringDeserializer())) {
                    List<TopicPartition> partitions = new ArrayList<>();
                    for (String topic : topics) {
                        consumer.partitionsFor(topic).forEach(partition ->
                                partitions.add(new TopicPartition(topic, partition.partition())));
                    }
                    consumer.assign(partitions);
                    consumer.seekToBeginning(partitions);
                    List<ConsumerRecord<String, String>> received = new ArrayList<>();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                    while (received.size() < 6 && System.nanoTime() < deadline) {
                        consumer.poll(Duration.ofMillis(250)).forEach(received::add);
                    }
                    assertThat(received).hasSize(6).allSatisfy(record ->
                            assertThat(record.key()).isEqualTo(jobId.toString()));
                    for (String topic : topics) {
                        assertThat(received.stream().filter(record -> record.topic().equals(topic))
                                .map(ConsumerRecord::value).toList()).containsExactlyElementsOf(
                                        snapshots.stream().filter(event -> event.getDestination().equals(topic))
                                                .sorted(Comparator.comparingLong(OutboxEvent::getJobVersion))
                                                .map(OutboxEvent::getPayload).toList());
                    }
                }
            } finally {
                events.deleteAllInBatch();
                admin.deleteTopics(topics).all().get(15, TimeUnit.SECONDS);
            }
        }
    }

    private void saveSnapshots(UUID jobId, List<String> topics) {
        var mapper = JsonMapper.builder().build();
        UUID clientId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        Instant timestamp = Instant.now().minusSeconds(30);
        // Persist in reverse order to check that job versions determine delivery order.
        for (int version : List.of(3, 2, 0)) {
            UUID eventId = UUID.randomUUID();
            var type = version == 0 ? JobAnalyticsEvent.Type.JOB_CREATED
                    : JobAnalyticsEvent.Type.JOB_STATUS_CHANGED;
            var status = version == 0 ? JobStatus.OPEN
                    : version == 2 ? JobStatus.ASSIGNED : JobStatus.COMPLETED;
            var data = new JobAnalyticsEvent.Data(jobId, clientId, status,
                    "PLUMBING", "CASABLANCA", timestamp);
            var analytics = new JobAnalyticsEvent(eventId, type, timestamp, data);
            var lifecycle = new JobLifecycleEvent(eventId, type, timestamp, data,
                    version == 0 ? null : workerId, (long) version);
            events.saveAndFlush(new OutboxEvent(eventId, type, topics.getFirst(), jobId, version,
                    mapper.writeValueAsString(analytics), timestamp));
            events.saveAndFlush(new OutboxEvent(eventId, type, topics.getLast(), jobId, version,
                    mapper.writeValueAsString(lifecycle), timestamp));
        }
    }
}
