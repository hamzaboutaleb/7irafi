package com.herafi.application_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.herafi.application_service.client.JobClient;
import com.herafi.application_service.client.NotificationClient;
import com.herafi.application_service.client.UserClient;
import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.entity.OutboxEvent;
import com.herafi.application_service.event.ApplicationAnalyticsEvent;
import com.herafi.application_service.event.JobLifecycleEvent;
import com.herafi.application_service.exceptions.ApplicationException;
import com.herafi.application_service.repository.JobApplicationRepository;
import com.herafi.application_service.repository.JobLifecycleStateRepository;
import com.herafi.application_service.repository.OutboxEventRepository;
import com.herafi.application_service.repository.ProcessedJobEventRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JobApplicationService.class, ApplicationSubmissionService.class, OutboxEventWriter.class,
        JobLifecycleEventHandler.class, OutboxEventDispatcher.class, ApplicationOutboxIntegrationTests.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationOutboxIntegrationTests {

    @Autowired private JobApplicationService service;
    @Autowired private JobLifecycleEventHandler handler;
    @Autowired private OutboxEventWriter writer;
    @Autowired private OutboxEventDispatcher dispatcher;
    @Autowired private JobApplicationRepository applications;
    @Autowired private OutboxEventRepository events;
    @Autowired private JobLifecycleStateRepository states;
    @Autowired private ProcessedJobEventRepository processed;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SimpleMeterRegistry meters;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JobClient jobs;
    @MockitoBean private UserClient users;
    @MockitoBean private NotificationClient notifications;
    @MockitoBean private KafkaTemplate<String, String> kafka;

    private final UUID jobId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();

    @BeforeEach
    void resetDatabase() {
        events.deleteAllInBatch();
        applications.deleteAllInBatch();
        processed.deleteAllInBatch();
        states.deleteAllInBatch();
        meters.clear();
        when(jobs.getSummary(jobId)).thenReturn(
                new JobClient.JobSummary(jobId, clientId, "OPEN", null, "PLUMBING", "CASABLANCA"));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void submissionCommitsAnApplicationAndOnePendingJsonSnapshot() {
        var response = service.apply(jobId, workerId);
        JobApplication application = applications.findById(response.id()).orElseThrow();

        assertThat(application.getJobSkill()).isEqualTo("PLUMBING");
        assertThat(application.getJobCity()).isEqualTo("CASABLANCA");
        assertThat(application.getAnalyticsStatus()).isEqualTo(ApplicationAnalyticsEvent.Status.PENDING);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(events.count()).isEqualTo(1);
        OutboxEvent row = events.findAll().getFirst();
        assertThat(row.getDestination()).isEqualTo("analytics.events");
        assertThat(row.getApplicationVersion()).isZero();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getNextAttemptAt()).isEqualTo(row.getCreatedAt());
        var event = event(row);
        assertThat(event.eventType()).isEqualTo(ApplicationAnalyticsEvent.Type.APPLICATION_SUBMITTED);
        assertThat(event.data().submittedAt()).isEqualTo(application.getCreatedAt());
        assertThat(event.data().applicationId()).isEqualTo(response.id());
        assertThat(event.data().applicantId()).isEqualTo(workerId);
        assertThat(mapper.readTree(jdbc.queryForObject(
                "SELECT payload FROM outbox_events WHERE id = ?", String.class, row.getId())).isObject()).isTrue();
        assertThat(meters.counter("herafi.applications.submitted").count()).isEqualTo(1);
        verify(notifications).notify(any(), any(), any(), any(), any());
    }

    @Test
    void assignmentAcceptsTheWinnerAndRejectsOtherApplicantsWithoutChangingSubmittedSnapshots() {
        var winner = service.apply(jobId, workerId);
        var other = service.apply(jobId, UUID.randomUUID());
        List<String> originalPayloads = events.findAll().stream().map(OutboxEvent::getPayload).toList();

        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 3));

        assertThat(events.count()).isEqualTo(4);
        assertThat(applications.findById(winner.id()).orElseThrow().getAnalyticsStatus())
                .isEqualTo(ApplicationAnalyticsEvent.Status.ACCEPTED);
        assertThat(applications.findById(other.id()).orElseThrow().getAnalyticsStatus())
                .isEqualTo(ApplicationAnalyticsEvent.Status.REJECTED);
        assertThat(events.findAll().stream().map(OutboxEvent::getPayload).toList()).containsAll(originalPayloads);
        for (OutboxEvent row : events.findAll()) {
            var application = applications.findById(row.getApplicationId()).orElseThrow();
            assertThat(event(row).data().submittedAt()).isEqualTo(application.getCreatedAt());
            assertThat(event(row).data().jobSkill()).isEqualTo("PLUMBING");
            assertThat(event(row).data().jobCity()).isEqualTo("CASABLANCA");
        }
    }

    @Test
    void cancellationRejectsPendingApplications() {
        service.apply(jobId, workerId);
        service.apply(jobId, UUID.randomUUID());

        handler.handle(lifecycle(JobLifecycleEvent.Status.CANCELLED, null, 1));

        assertThat(events.count()).isEqualTo(4);
        assertThat(applications.findAll()).allSatisfy(application ->
                assertThat(application.getAnalyticsStatus()).isEqualTo(ApplicationAnalyticsEvent.Status.REJECTED));
        assertThat(events.findAll().stream().filter(row -> row.getApplicationVersion() > 0))
                .allSatisfy(row -> assertThat(row.getEventType())
                        .isEqualTo(ApplicationAnalyticsEvent.Type.APPLICATION_REJECTED));
    }

    @Test
    void duplicateIdsEqualVersionsAndOlderSnapshotsDoNotRepeatOrUndoOutcomes() {
        service.apply(jobId, workerId);
        var assignment = lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 3);
        handler.handle(assignment);
        handler.handle(assignment);
        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 3));
        handler.handle(lifecycle(JobLifecycleEvent.Status.CANCELLED, null, 1));
        handler.handle(lifecycle(JobLifecycleEvent.Status.COMPLETED, workerId, 8));

        assertThat(events.count()).isEqualTo(2);
        assertThat(processed.count()).isEqualTo(4);
        assertThat(states.findById(jobId).orElseThrow().getJobVersion()).isEqualTo(8);
        assertThat(applications.findAll().getFirst().getAnalyticsStatus())
                .isEqualTo(ApplicationAnalyticsEvent.Status.ACCEPTED);
    }

    @Test
    void deletionDoesNotInventAnApplicationTransitionAndPreventsAStaleOpenSubmission() {
        service.apply(jobId, workerId);
        var open = lifecycle(JobLifecycleEvent.Status.OPEN, null, 1);
        handler.handle(new JobLifecycleEvent(open.eventId(), JobLifecycleEvent.Type.JOB_DELETED,
                open.timestamp(), open.data(), null, 1L));

        assertThat(events.count()).isEqualTo(1);
        assertThat(states.findById(jobId).orElseThrow().isDeleted()).isTrue();
        assertThatThrownBy(() -> service.apply(jobId, UUID.randomUUID()))
                .isInstanceOf(ApplicationException.class).hasMessageContaining("open jobs");
    }

    @Test
    void duplicateApplicationsProduceNoExtraEventOrSuccessMetric() {
        service.apply(jobId, workerId);

        assertThatThrownBy(() -> service.apply(jobId, workerId)).isInstanceOf(ApplicationException.class)
                .hasMessageContaining("already applied");

        assertThat(applications.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
        assertThat(meters.counter("herafi.applications.submitted").count()).isEqualTo(1);
    }

    @Test
    void rollingBackSubmissionRemovesApplicationOutboxAndDeferredSideEffects() {
        assertThatThrownBy(() -> transactions().executeWithoutResult(transaction -> {
            service.apply(jobId, workerId);
            events.flush();
            throw new IllegalStateException("Abort submission");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(applications.count()).isZero();
        assertThat(events.count()).isZero();
        assertThat(meters.find("herafi.applications.submitted").counter()).isNull();
        verifyNoInteractions(notifications);
    }

    @Test
    void anOutboxConstraintFailureAlsoRollsBackTheApplication() {
        assertThatThrownBy(() -> transactions().executeWithoutResult(transaction -> {
            service.apply(jobId, workerId);
            events.flush();
            OutboxEvent original = events.findAll().getFirst();
            events.saveAndFlush(new OutboxEvent(original.getEventId(), original.getEventType(),
                    original.getDestination(), original.getApplicationId(), original.getApplicationVersion(),
                    original.getPayload(), original.getCreatedAt()));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(applications.count()).isZero();
        assertThat(events.count()).isZero();
        verifyNoInteractions(notifications);
    }

    @Test
    void rollingBackConsumptionRestoresApplicationStateInboxAndOutboxTogether() {
        service.apply(jobId, workerId);

        assertThatThrownBy(() -> transactions().executeWithoutResult(transaction -> {
            handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 2));
            events.flush();
            throw new IllegalStateException("Abort consumption");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(events.count()).isEqualTo(1);
        assertThat(processed.count()).isZero();
        assertThat(states.findById(jobId).orElseThrow().getJobVersion()).isEqualTo(-1);
        assertThat(applications.findAll().getFirst().getAnalyticsStatus())
                .isEqualTo(ApplicationAnalyticsEvent.Status.PENDING);
    }

    @Test
    void legacyApplicationsReceiveMetadataFromTheLifecycleSnapshotWithoutHistoricalSubmissionEvents() {
        JobApplication legacy = new JobApplication();
        legacy.setJobId(jobId);
        legacy.setApplicantId(workerId);
        applications.saveAndFlush(legacy);

        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 2));

        assertThat(events.count()).isEqualTo(1);
        assertThat(event(events.findAll().getFirst()).data().jobSkill()).isEqualTo("PLUMBING");
        assertThat(event(events.findAll().getFirst()).eventType())
                .isEqualTo(ApplicationAnalyticsEvent.Type.APPLICATION_ACCEPTED);
        verifyNoInteractions(jobs, users, notifications);
    }

    @Test
    void theWriterRequiresTheApplicationTransaction() {
        service.apply(jobId, workerId);
        var application = applications.findAll().getFirst();
        assertThatThrownBy(() -> writer.recordApplicationEvent(application,
                ApplicationAnalyticsEvent.Type.APPLICATION_SUBMITTED, Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void anAssignmentWaitsForAnInFlightSubmissionAndThenResolvesIt() throws Exception {
        CountDownLatch submitted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var submission = executor.submit(() -> transactions().executeWithoutResult(transaction -> {
                service.apply(jobId, workerId);
                submitted.countDown();
                await(commit);
            }));
            try {
                assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();
                verifyNoInteractions(notifications);
                var assignment = executor.submit(() ->
                        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 2)));
                assertThatThrownBy(() -> assignment.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                commit.countDown();
                submission.get(5, TimeUnit.SECONDS);
                assignment.get(5, TimeUnit.SECONDS);
            } finally {
                commit.countDown();
            }
        }
        assertThat(events.count()).isEqualTo(2);
        assertThat(applications.findAll().getFirst().getAnalyticsStatus())
                .isEqualTo(ApplicationAnalyticsEvent.Status.ACCEPTED);
    }

    @Test
    void aSubmissionWaitsForAnInFlightClosureAndRejectsItsStaleHttpSummary() throws Exception {
        CountDownLatch cancelled = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var closure = executor.submit(() -> transactions().executeWithoutResult(transaction -> {
                handler.handle(lifecycle(JobLifecycleEvent.Status.CANCELLED, null, 2));
                cancelled.countDown();
                await(commit);
            }));
            try {
                assertThat(cancelled.await(5, TimeUnit.SECONDS)).isTrue();
                var submission = executor.submit(() -> service.apply(jobId, workerId));
                assertThatThrownBy(() -> submission.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                commit.countDown();
                closure.get(5, TimeUnit.SECONDS);
                assertThatThrownBy(() -> submission.get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(ApplicationException.class);
            } finally {
                commit.countDown();
            }
        }
        assertThat(applications.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test
    void publicationUsesTheApplicationKeyAndSendsSubmissionBeforeItsOutcome() {
        var application = service.apply(jobId, workerId);
        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 2));

        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isFalse();

        var payloads = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(eq("analytics.events"), eq(application.id().toString()), payloads.capture());
        assertThat(payloads.getAllValues().stream().map(json -> mapper.readValue(json, ApplicationAnalyticsEvent.class))
                .map(ApplicationAnalyticsEvent::eventType).toList()).containsExactly(
                        ApplicationAnalyticsEvent.Type.APPLICATION_SUBMITTED,
                        ApplicationAnalyticsEvent.Type.APPLICATION_ACCEPTED);
        assertThat(events.findAll()).allSatisfy(row -> assertThat(row.getPublishedAt()).isNotNull());
    }

    @Test
    void brokerFailuresBlockOnlyThatApplicationsSuccessorsAndRetryTheOriginalEvent() {
        var application = service.apply(jobId, workerId);
        OutboxEvent first = events.findAll().getFirst();
        service.apply(jobId, UUID.randomUUID());
        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 2));
        when(kafka.send("analytics.events", application.id().toString(), first.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker down")));

        assertThat(dispatcher.publishNext()).isFalse();
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isFalse();
        OutboxEvent failed = events.findById(first.getId()).orElseThrow();
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("Broker down");
        assertThat(failed.getNextAttemptAt()).isAfter(failed.getCreatedAt());
        jdbc.update("UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?",
                Timestamp.from(Instant.EPOCH), first.getId());
        doReturn(CompletableFuture.completedFuture(null)).when(kafka)
                .send("analytics.events", application.id().toString(), first.getPayload());

        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(events.findById(first.getId()).orElseThrow().getEventId()).isEqualTo(first.getEventId());
        assertThat(events.findById(first.getId()).orElseThrow().getAttempts()).isEqualTo(2);
        assertThat(events.findById(first.getId()).orElseThrow().getLastError()).isNull();
        verify(kafka, times(2)).send("analytics.events", application.id().toString(), first.getPayload());
    }

    @Test
    void anotherPublisherCannotTakeALockedApplicationOrPublishItsNextVersionEarly() throws Exception {
        var application = service.apply(jobId, workerId);
        OutboxEvent first = events.findAll().getFirst();
        handler.handle(lifecycle(JobLifecycleEvent.Status.ASSIGNED, workerId, 2));
        var acknowledgement = new CompletableFuture<SendResult<String, String>>();
        CountDownLatch sending = new CountDownLatch(1);
        when(kafka.send("analytics.events", application.id().toString(), first.getPayload())).thenAnswer(call -> {
            sending.countDown();
            return acknowledgement;
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var publication = executor.submit(dispatcher::publishNext);
            try {
                assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(events.findById(first.getId()).orElseThrow().getPublishedAt()).isNull();
                assertThat(executor.submit(dispatcher::publishNext).get(5, TimeUnit.SECONDS)).isFalse();
            } finally {
                acknowledgement.complete(null);
            }
            assertThat(publication.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(dispatcher.publishNext()).isTrue();
    }

    @Test
    void aDatabaseFailureAfterAcknowledgementReplaysTheSameEvent() {
        var application = service.apply(jobId, workerId);
        OutboxEvent first = events.findAll().getFirst();
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("Database commit failed");
                }
            });
            return CompletableFuture.completedFuture(null);
        });
        assertThatThrownBy(dispatcher::publishNext).isInstanceOf(IllegalStateException.class);
        assertThat(events.findById(first.getId()).orElseThrow().getPublishedAt()).isNull();
        doReturn(CompletableFuture.completedFuture(null)).when(kafka)
                .send(anyString(), anyString(), anyString());

        assertThat(dispatcher.publishNext()).isTrue();
        verify(kafka, times(2)).send("analytics.events", application.id().toString(), first.getPayload());
    }

    private JobLifecycleEvent lifecycle(JobLifecycleEvent.Status status, UUID assignedWorker, long version) {
        var data = new JobLifecycleEvent.Data(jobId, clientId, status, "PLUMBING", "CASABLANCA",
                Instant.now().minusSeconds(60));
        return new JobLifecycleEvent(UUID.randomUUID(), status == JobLifecycleEvent.Status.OPEN
                ? JobLifecycleEvent.Type.JOB_CREATED : JobLifecycleEvent.Type.JOB_STATUS_CHANGED,
                Instant.now(), data, assignedWorker, version);
    }

    private ApplicationAnalyticsEvent event(OutboxEvent row) {
        return mapper.readValue(row.getPayload(), ApplicationAnalyticsEvent.class);
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test transaction");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
        @Bean SimpleMeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
