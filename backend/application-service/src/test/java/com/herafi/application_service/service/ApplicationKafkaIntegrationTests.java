package com.herafi.application_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import com.herafi.application_service.client.JobClient;
import com.herafi.application_service.client.UserClient;
import com.herafi.application_service.event.ApplicationAnalyticsEvent;
import com.herafi.application_service.event.JobLifecycleEvent;
import com.herafi.application_service.repository.JobApplicationRepository;
import com.herafi.application_service.repository.OutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

/** Requires a disposable broker: this test uses the real analytics.events/jobs.lifecycle topics. */
@EnabledIfEnvironmentVariable(named = "APPLICATION_KAFKA_TEST_BOOTSTRAP_SERVERS", matches = ".+")
@SpringBootTest(properties = {"applications.outbox.enabled=true", "applications.lifecycle.enabled=true"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationKafkaIntegrationTests {

    private static final String GROUP = "application-test-" + UUID.randomUUID();

    @Autowired private JobApplicationService service;
    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private JobApplicationRepository applications;
    @Autowired private OutboxEventRepository events;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private OutboxEventWriter writer;
    @MockitoBean private JobClient jobs;
    @MockitoBean private UserClient users;
    @MockitoBean private NotificationPublisher notifications;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", ApplicationKafkaIntegrationTests::broker);
        registry.add("spring.kafka.consumer.group-id", () -> GROUP);
    }

    @BeforeAll
    static void createTopics() throws Exception {
        try (Admin admin = Admin.create(Map.of("bootstrap.servers", broker()))) {
            var results = admin.createTopics(List.of(new NewTopic("analytics.events", 3, (short) 1),
                    new NewTopic("jobs.lifecycle", 3, (short) 1)));
            for (var result : results.values().values()) {
                try {
                    result.get(15, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException failure) {
                    if (!(failure.getCause() instanceof TopicExistsException)) {
                        throw failure;
                    }
                }
            }
        }
    }

    @Test
    void scheduledPublisherAndKafkaListenerDeliverAllApplicationEventsDespiteARetriedTransaction() throws Exception {
        events.deleteAllInBatch();
        applications.deleteAllInBatch();
        UUID jobId = UUID.randomUUID();
        UUID cancelledJobId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        when(jobs.getSummary(jobId)).thenReturn(
                new JobClient.JobSummary(jobId, clientId, "OPEN", null, "PLUMBING", "CASABLANCA"));
        when(jobs.getSummary(cancelledJobId)).thenReturn(
                new JobClient.JobSummary(cancelledJobId, clientId, "OPEN", null, "PAINTING", "RABAT"));
        var winner = service.apply(jobId, workerId);
        var other = service.apply(jobId, UUID.randomUUID());
        var cancelled = service.apply(cancelledJobId, UUID.randomUUID());
        AtomicBoolean failOnce = new AtomicBoolean(true);
        OutboxEventWriter writerTarget = AopTestUtils.getUltimateTargetObject(writer);
        doAnswer(call -> {
            if (failOnce.compareAndSet(true, false)) {
                throw new IllegalStateException("Simulated transient database write failure");
            }
            return call.callRealMethod();
        }).when(writerTarget).recordApplicationEvent(any(), eq(ApplicationAnalyticsEvent.Type.APPLICATION_ACCEPTED), any());

        var assignment = lifecycle(jobId, clientId, JobLifecycleEvent.Status.ASSIGNED, workerId, 3);
        send(assignment);
        send(assignment);
        send(lifecycle(jobId, clientId, JobLifecycleEvent.Status.OPEN, null, 0));
        send(lifecycle(jobId, clientId, JobLifecycleEvent.Status.COMPLETED, workerId, 8));
        send(lifecycle(cancelledJobId, clientId, JobLifecycleEvent.Status.CANCELLED, null, 1));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(events.count()).isEqualTo(6);
            assertThat(events.findAll()).allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
        });
        assertThat(failOnce).isFalse();
        List<UUID> ids = List.of(winner.id(), other.id(), cancelled.id());
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                "bootstrap.servers", broker(), "enable.auto.commit", false),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.assign(consumer.partitionsFor("analytics.events").stream()
                    .map(partition -> new TopicPartition("analytics.events", partition.partition())).toList());
            consumer.seekToBeginning(consumer.assignment());
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (received.size() < 6 && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    var event = mapper.readValue(record.value(), ApplicationAnalyticsEvent.class);
                    if (ids.contains(event.data().applicationId())) {
                        received.add(record);
                        assertThat(record.key()).isEqualTo(event.data().applicationId().toString());
                    }
                }
            }
            assertThat(received).hasSize(6);
            for (UUID id : ids) {
                assertThat(received.stream().filter(record -> record.key().equals(id.toString()))
                        .map(record -> mapper.readValue(record.value(), ApplicationAnalyticsEvent.class))
                        .map(ApplicationAnalyticsEvent::eventType).toList()).containsExactly(
                                ApplicationAnalyticsEvent.Type.APPLICATION_SUBMITTED,
                                id.equals(winner.id()) ? ApplicationAnalyticsEvent.Type.APPLICATION_ACCEPTED
                                        : ApplicationAnalyticsEvent.Type.APPLICATION_REJECTED);
            }
        }
    }

    private void send(JobLifecycleEvent event) throws Exception {
        kafka.send("jobs.lifecycle", event.data().jobId().toString(), mapper.writeValueAsString(event))
                .get(10, TimeUnit.SECONDS);
    }

    private JobLifecycleEvent lifecycle(UUID jobId, UUID clientId, JobLifecycleEvent.Status status,
            UUID workerId, long version) {
        var data = new JobLifecycleEvent.Data(jobId, clientId, status, "PLUMBING", "CASABLANCA",
                Instant.now().minusSeconds(60));
        return new JobLifecycleEvent(UUID.randomUUID(), status == JobLifecycleEvent.Status.OPEN
                ? JobLifecycleEvent.Type.JOB_CREATED : JobLifecycleEvent.Type.JOB_STATUS_CHANGED,
                Instant.now(), data, workerId, version);
    }

    private static String broker() {
        return System.getenv("APPLICATION_KAFKA_TEST_BOOTSTRAP_SERVERS");
    }
}
