package com.herafi.analytics;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.dto.JobStatus;
import com.herafi.analytics.listener.AnalyticsEventListener;
import com.herafi.analytics.repository.ProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.MessageDeliveryException;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
@ExtendWith(OutputCaptureExtension.class)
class AnalyticsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired AnalyticsEventListener eventListener;
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

    @Test
    void projectsApplicationStatusChangesAndSnapshotsWithoutSubmissionHistory() throws Exception {
        UUID applicant = UUID.randomUUID();
        UUID acceptedId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var submitted = EventFixtures.application(applicant, acceptedId, jobId, ApplicationEvent.Type.APPLICATION_SUBMITTED);
        var accepted = EventFixtures.application(applicant, acceptedId, jobId, ApplicationEvent.Type.APPLICATION_ACCEPTED);
        var rejected = EventFixtures.application(applicant, rejectedId, jobId, ApplicationEvent.Type.APPLICATION_REJECTED);
        send(acceptedId, submitted);
        send(acceptedId, accepted);
        send(rejectedId, rejected);
        awaitProcessed(rejected.eventId());

        assertThat(jdbc.queryForObject("SELECT status FROM analytics_applications WHERE application_id = ?", String.class, acceptedId))
                .isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT status FROM analytics_applications WHERE application_id = ?", String.class, rejectedId))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM analytics_applications WHERE application_id = ?",
                java.sql.Timestamp.class, acceptedId).toInstant()).isEqualTo(EventFixtures.CREATED_AT);
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM analytics_applications WHERE application_id = ?",
                java.sql.Timestamp.class, rejectedId).toInstant()).isEqualTo(EventFixtures.CREATED_AT);
    }

    static java.util.stream.Stream<RuntimeException> projectionFailures() {
        return java.util.stream.Stream.of(
                new TransientDataAccessResourceException("Simulated database outage"),
                new ClassCastException("Simulated contract handling bug"));
    }

    @ParameterizedTest
    @MethodSource("projectionFailures")
    void retriesProjectionFailuresWithoutLosingOrReorderingEvents(RuntimeException failure, CapturedOutput output) throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var event = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        var cancelled = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_STATUS_CHANGED, JobStatus.CANCELLED);
        doThrow(failure)
                .doCallRealMethod().when(repository).saveJob(argThat(job -> job.jobId().equals(jobId)));

        var metadata = send(jobId, event);
        send(jobId, cancelled);
        awaitProcessed(cancelled.eventId());

        assertThat(output.getAll()).contains(
                "Analytics event failed; retrying topic=analytics.events partition=" + metadata.partition()
                        + " offset=" + metadata.offset() + " attempt=1");
        verify(repository, times(2)).saveJob(event.data());
        assertThat(receipts(event.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM analytics_jobs WHERE job_id = ?", String.class, jobId))
                .isEqualTo("CANCELLED");
        await().untilAsserted(() -> verify(messaging, times(2))
                .convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class)));
    }

    @Test
    void notifiesOnlyAfterTheProjectionIsVisibleToAnotherConnection() throws Exception {
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

        eventListener.consume(mapper.writeValueAsString(event));

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
    void rollsBackEventReceiptAndDoesNotNotifyWhenDatabaseWriteFails() throws Exception {
        UUID owner = UUID.randomUUID();
        var data = new JobEvent.Data(UUID.randomUUID(), owner, JobStatus.OPEN,
                "X".repeat(101), "CASABLANCA", EventFixtures.CREATED_AT);
        var event = new JobEvent(UUID.randomUUID(), JobEvent.Type.JOB_CREATED, EventFixtures.OCCURRED_AT, data);

        String payload = mapper.writeValueAsString(event);
        assertThatThrownBy(() -> eventListener.consume(payload)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(receipts(event.eventId())).isZero();
        verify(messaging, never()).convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));
    }

    @Test
    void aCommitFailurePreventsNotificationAndAllowsRetry() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var event = EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN);
        String payload = mapper.writeValueAsString(event);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new TransientDataAccessResourceException("Simulated commit failure");
                }
            });
            return null;
        }).doCallRealMethod().when(repository).saveJob(argThat(job -> job.jobId().equals(jobId)));

        assertThatThrownBy(() -> eventListener.consume(payload))
                .isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(receipts(event.eventId())).isZero();
        verify(messaging, never()).convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));

        eventListener.consume(payload);

        assertThat(receipts(event.eventId())).isEqualTo(1);
        verify(messaging, times(1)).convertAndSendToUser(eq(owner.toString()), eq("/queue/analytics"), any(Object.class));
    }

    private RecordMetadata send(UUID key, AnalyticsEvent event) throws Exception {
        return kafka.send("analytics.events", key.toString(), mapper.writeValueAsString(event)).get().getRecordMetadata();
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(receipts(eventId)).isEqualTo(1));
    }

    private long receipts(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_processed_events WHERE event_id = ?", Long.class, eventId);
    }
}
