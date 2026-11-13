package com.herafi.analytics;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.dto.JobStatus;
import com.herafi.analytics.repository.ProjectionRepository;
import com.herafi.analytics.service.ProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=analytics-test"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = "analytics.events")
@DirtiesContext
class AnalyticsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired ProjectionService projections;
    @Autowired KafkaListenerEndpointRegistry listeners;
    @SpyBean ProjectionRepository repository;
    @SpyBean SimpMessagingTemplate messaging;

    @Test
    void preservesKafkaOrderAndIgnoresDuplicateEvents() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var created = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        var cancelled = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_STATUS_CHANGED, JobStatus.CANCELLED);
        send(jobId, created);
        send(jobId, created);
        send(jobId, cancelled);
        awaitProcessed(cancelled.eventId());

        assertThat(jdbc.queryForObject("SELECT status FROM analytics_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("CANCELLED");
        assertThat(receipts(created.eventId())).isEqualTo(1);
        await().untilAsserted(() -> verify(messaging, times(2))
                .convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class)));
    }

    @Test
    void deletesJobsWithoutDeletingApplicationHistory() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        var application = EventFixtures.application(UUID.randomUUID(), applicationId, jobId, ApplicationEvent.Type.APPLICATION_SUBMITTED);
        var deleted = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_DELETED, JobStatus.OPEN);
        send(jobId, EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN));
        send(applicationId, application);
        send(jobId, deleted);
        awaitProcessed(deleted.eventId());

        assertThat(jdbc.queryForList("SELECT job_id FROM analytics_jobs WHERE job_id = ?", UUID.class, jobId)).isEmpty();
        assertThat(jdbc.queryForList("SELECT application_id FROM analytics_applications WHERE application_id = ?",
                UUID.class, applicationId)).containsExactly(applicationId);
    }

    @ParameterizedTest
    @EnumSource(ApplicationEvent.Type.class)
    void acceptsApplicationSnapshotsEvenWhenSubmissionIsOutsideRetention(ApplicationEvent.Type type) throws Exception {
        UUID id = UUID.randomUUID();
        var event = EventFixtures.application(UUID.randomUUID(), id, UUID.randomUUID(), type);
        send(id, event);
        awaitProcessed(event.eventId());

        assertThat(jdbc.queryForObject("SELECT status FROM analytics_applications WHERE application_id = ?", String.class, id))
                .isEqualTo(type.status().name());
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM analytics_applications WHERE application_id = ?",
                java.sql.Timestamp.class, id).toInstant()).isEqualTo(EventFixtures.CREATED_AT);
    }

    static java.util.stream.Stream<RuntimeException> projectionFailures() {
        return java.util.stream.Stream.of(
                new TransientDataAccessResourceException("Simulated database outage"),
                new ClassCastException("Simulated contract handling bug"));
    }

    @ParameterizedTest
    @MethodSource("projectionFailures")
    void retriesProjectionFailuresWithoutLosingOrReorderingEvents(RuntimeException failure) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var event = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        var cancelled = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_STATUS_CHANGED, JobStatus.CANCELLED);
        doThrow(failure)
                .doCallRealMethod().when(repository).saveJob(argThat(job -> job.jobId().equals(jobId)));

        send(jobId, event);
        send(jobId, cancelled);
        awaitProcessed(cancelled.eventId());

        verify(repository, times(2)).saveJob(event.data());
        assertThat(receipts(event.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM analytics_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("CANCELLED");
        await().untilAsserted(() -> verify(messaging, times(2))
                .convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class)));
    }

    @Test
    void notifiesOnlyAfterTheProjectionIsVisibleToAnotherConnection() {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var event = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        doAnswer(invocation -> {
            try (var connection = jdbc.getDataSource().getConnection();
                 var statement = connection.prepareStatement("SELECT status FROM analytics_jobs WHERE job_id = ?")) {
                statement.setObject(1, jobId);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("OPEN");
                }
            }
            return null;
        }).when(messaging).convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));

        projections.apply(event);

        verify(messaging).convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));
    }

    @Test
    void aSocketDeliveryFailureDoesNotUndoTheProjectionOrBlockLaterEvents() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var created = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        var cancelled = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_STATUS_CHANGED, JobStatus.CANCELLED);
        doThrow(new MessageDeliveryException("Simulated broker outage"))
                .doCallRealMethod().when(messaging)
                .convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));

        send(jobId, created);
        send(jobId, cancelled);
        awaitProcessed(cancelled.eventId());

        assertThat(receipts(created.eventId())).isEqualTo(1);
        verify(repository, times(1)).saveJob(created.data());
        assertThat(jdbc.queryForObject("SELECT status FROM analytics_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("CANCELLED");
    }

    @Test
    void rollsBackEventReceiptAndDoesNotNotifyWhenDatabaseWriteFails() {
        UUID owner = UUID.randomUUID();
        var data = new JobEvent.Data(UUID.randomUUID(), owner, JobStatus.OPEN,
                "X".repeat(101), "CASABLANCA", EventFixtures.CREATED_AT);
        var event = new JobEvent(UUID.randomUUID(), JobEvent.Type.JOB_CREATED, EventFixtures.OCCURRED_AT, data);

        assertThatThrownBy(() -> projections.apply(event)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(receipts(event.eventId())).isZero();
        verify(messaging, never()).convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));
    }

    @Test
    void resumesAfterListenerRestartAndDeduplicatesReplay() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var created = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        var assigned = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_STATUS_CHANGED, JobStatus.ASSIGNED);
        send(jobId, created);
        awaitProcessed(created.eventId());

        var listener = listeners.getListenerContainer("analytics-events");
        listener.stop();
        try {
            send(jobId, created);
            send(jobId, assigned);
            assertThat(receipts(assigned.eventId())).isZero();
        } finally {
            listener.start();
        }
        awaitProcessed(assigned.eventId());
        assertThat(jdbc.queryForObject("SELECT status FROM analytics_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("ASSIGNED");
        assertThat(receipts(created.eventId())).isEqualTo(1);
    }

    private void send(UUID key, AnalyticsEvent event) throws Exception {
        kafka.send("analytics.events", key.toString(), mapper.writeValueAsString(event)).get();
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(receipts(eventId)).isEqualTo(1));
    }

    private long receipts(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_processed_events WHERE event_id = ?", Long.class, eventId);
    }
}
