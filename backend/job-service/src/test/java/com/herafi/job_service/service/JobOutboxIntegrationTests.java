package com.herafi.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.herafi.job_service.config.JobImageProperties;
import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.UpdateJobRequest;
import com.herafi.job_service.entity.City;
import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.OutboxEvent;
import com.herafi.job_service.entity.Skill;
import com.herafi.job_service.event.JobAnalyticsEvent;
import com.herafi.job_service.event.JobLifecycleEvent;
import com.herafi.job_service.exceptions.InvalidJobStateException;
import com.herafi.job_service.mapper.JobMapper;
import com.herafi.job_service.repository.CityRepository;
import com.herafi.job_service.repository.JobImageRepository;
import com.herafi.job_service.repository.JobRepository;
import com.herafi.job_service.repository.OutboxEventRepository;
import com.herafi.job_service.repository.SkillRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JobService.class, OutboxEventWriter.class, JobOutboxIntegrationTests.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobOutboxIntegrationTests {

    @Autowired private JobService jobService;
    @Autowired private OutboxEventWriter outboxEventWriter;
    @Autowired private JobRepository jobs;
    @Autowired private OutboxEventRepository events;
    @Autowired private JobImageRepository images;
    @Autowired private SkillRepository skills;
    @Autowired private CityRepository cities;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SimpleMeterRegistry meters;

    @MockitoBean private ImageStorageService imageStorageService;
    @MockitoBean private JobMapper jobMapper;

    private final UUID clientId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();

    @BeforeEach
    void resetDatabase() {
        transactions().executeWithoutResult(transaction -> {
            events.deleteAllInBatch();
            images.deleteAllInBatch();
            jobs.deleteAllInBatch();

            Skill skill = new Skill();
            skill.setCode("PLUMBING");
            skills.save(skill);
            City city = new City();
            city.setCode("CASABLANCA");
            cities.save(city);
        });
        meters.clear();
    }

    @Test
    void creationCommitsTwoPendingJsonObjectsWithThePublicAndInternalContracts() {
        Job job = createJob();

        assertThat(jobService.getSummary(job.getId()).skill()).isEqualTo("PLUMBING");
        assertThat(jobService.getSummary(job.getId()).city()).isEqualTo("CASABLANCA");
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(2);
        assertPair(job, 0L, JobAnalyticsEvent.Type.JOB_CREATED, JobStatus.OPEN, null);

        for (OutboxEvent event : events.findAll()) {
            String storedJson = jdbc.queryForObject(
                    "SELECT payload FROM outbox_events WHERE id = ?", String.class, event.getId());
            assertThat(objectMapper.readTree(storedJson).isObject()).isTrue();
        }
        assertThat(meters.counter("herafi.jobs.posted").count()).isEqualTo(1.0);
    }

    @Test
    void assignmentAndCompletionCaptureFlushedVersionsAndPreserveEarlierSnapshots() {
        Job created = createJob();

        var assignedSummary = jobService.assignWorker(created.getId(), workerId);
        assertThat(assignedSummary.skill()).isEqualTo("PLUMBING");
        assertThat(assignedSummary.city()).isEqualTo("CASABLANCA");
        Job completed = jobService.complete(created.getId(), clientId);

        assertThat(events.count()).isEqualTo(6);
        assertPair(created, 0L, JobAnalyticsEvent.Type.JOB_CREATED, JobStatus.OPEN, null);
        assertPair(completed, 1L, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                JobStatus.ASSIGNED, workerId);
        assertPair(completed, 2L, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                JobStatus.COMPLETED, workerId);
        assertThat(jobs.findById(created.getId()).orElseThrow().getVersion()).isEqualTo(2L);
    }

    @Test
    void cancellationRecordsTheCancelledSnapshotWithoutAnAssignedWorker() {
        Job job = createJob();

        Job cancelled = jobService.cancelJob(job.getId(), clientId, false);

        assertThat(events.count()).isEqualTo(4);
        assertPair(cancelled, 1L, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                JobStatus.CANCELLED, null);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"OPEN", "COMPLETED", "CANCELLED"})
    void softDeletionRecordsTheLastStatusAndOriginalCreationTime(JobStatus lastStatus) {
        Job created = createJob();
        if (lastStatus == JobStatus.COMPLETED) {
            jobService.assignWorker(created.getId(), workerId);
            jobService.complete(created.getId(), clientId);
        } else if (lastStatus == JobStatus.CANCELLED) {
            jobService.cancelJob(created.getId(), clientId, false);
        }
        long previousCount = events.count();

        jobService.deleteJob(created.getId(), clientId, false);

        Job deleted = jobs.findById(created.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(jobs.findByIdAndDeletedFalse(created.getId())).isEmpty();
        assertThat(events.count()).isEqualTo(previousCount + 2);
        assertPair(deleted, deleted.getVersion(), JobAnalyticsEvent.Type.JOB_DELETED,
                lastStatus,
                lastStatus == JobStatus.COMPLETED ? workerId : null);
    }

    @Test
    void assigningTheSameWorkerAgainDoesNotCreateAnotherEventOrVersion() {
        Job job = createJob();
        jobService.assignWorker(job.getId(), workerId);

        var repeated = jobService.assignWorker(job.getId(), workerId);

        assertThat(repeated.skill()).isEqualTo("PLUMBING");
        assertThat(repeated.city()).isEqualTo("CASABLANCA");
        assertThat(events.count()).isEqualTo(4);
        assertThat(jobs.findById(job.getId()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void unrelatedEditsEmitNoEventAndTheNextLifecycleSnapshotAllowsAVersionGap() {
        Job job = createJob();
        jobService.update(job.getId(), clientId,
                new UpdateJobRequest("Updated title", "Updated description", "Private address"));
        assertThat(events.count()).isEqualTo(2);

        jobService.assignWorker(job.getId(), workerId);

        Job assigned = jobs.findById(job.getId()).orElseThrow();
        assertThat(events.count()).isEqualTo(4);
        assertPair(assigned, 2L, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                JobStatus.ASSIGNED, workerId);
    }

    @Test
    void rollingBackCreationRemovesTheJobAndBothEventsAndDoesNotCountASuccess() {
        assertThatThrownBy(() -> transactions().executeWithoutResult(transaction -> {
            createJob();
            events.flush();
            throw new IllegalStateException("Abort the job transaction");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jobs.count()).isZero();
        assertThat(events.count()).isZero();
        assertThat(meters.find("herafi.jobs.posted").counter()).isNull();
    }

    @Test
    void rollingBackAssignmentRestoresTheJobAndRemovesOnlyTheNewEvents() {
        Job job = createJob();

        assertThatThrownBy(() -> transactions().executeWithoutResult(transaction -> {
            jobService.assignWorker(job.getId(), workerId);
            events.flush();
            throw new IllegalStateException("Abort the assignment transaction");
        })).isInstanceOf(IllegalStateException.class);

        Job unchanged = jobs.findById(job.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(unchanged.getAssignedWorkerId()).isNull();
        assertThat(unchanged.getVersion()).isZero();
        assertThat(events.count()).isEqualTo(2);
    }

    @Test
    void anOutboxConstraintFailureRollsBackTheJobAndPreviouslyFlushedEvents() {
        assertThatThrownBy(() -> transactions().executeWithoutResult(transaction -> {
            createJob();
            events.flush();
            OutboxEvent original = events.findAll().getFirst();
            events.saveAndFlush(new OutboxEvent(original.getEventId(), original.getEventType(),
                    original.getDestination(), original.getJobId(), original.getJobVersion(),
                    original.getPayload(), original.getCreatedAt()));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jobs.count()).isZero();
        assertThat(events.count()).isZero();
        assertThat(meters.find("herafi.jobs.posted").counter()).isNull();
    }

    @Test
    void aJobConstraintFailureLeavesNoOutboxRows() {
        assertThatThrownBy(() -> jobService.create(null, request()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jobs.count()).isZero();
        assertThat(events.count()).isZero();
    }

    @Test
    void theWriterCannotRecordAnEventOutsideTheJobTransaction() {
        Job job = createJob();

        assertThatThrownBy(() -> outboxEventWriter.recordJobEvent(job, JobAnalyticsEvent.Type.JOB_CREATED))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(events.count()).isEqualTo(2);
    }

    @Test
    void rejectedStatusChangesLeaveTheJobAndOutboxUnchanged() {
        Job job = createJob();

        assertThatThrownBy(() -> jobService.complete(job.getId(), clientId))
                .isInstanceOf(InvalidJobStateException.class);

        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(events.count()).isEqualTo(2);
    }

    private void assertPair(Job job, long version, JobAnalyticsEvent.Type type,
            JobStatus status, UUID assignedWorker) {
        List<OutboxEvent> pair = events.findAll().stream()
                .filter(event -> event.getJobId().equals(job.getId()) && event.getJobVersion() == version)
                .toList();
        assertThat(pair).hasSize(2);
        assertThat(pair).extracting(OutboxEvent::getDestination)
                .containsExactlyInAnyOrder("analytics.events", "jobs.lifecycle");
        assertThat(pair).extracting(OutboxEvent::getEventId).containsOnly(pair.getFirst().getEventId());
        assertThat(pair).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(type);
            assertThat(event.getPublishedAt()).isNull();
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getLastError()).isNull();
            assertThat(event.getNextAttemptAt()).isEqualTo(event.getCreatedAt());
        });

        OutboxEvent analyticsRow = pair.stream()
                .filter(event -> event.getDestination().equals("analytics.events")).findFirst().orElseThrow();
        OutboxEvent lifecycleRow = pair.stream()
                .filter(event -> event.getDestination().equals("jobs.lifecycle")).findFirst().orElseThrow();
        var analytics = objectMapper.readValue(analyticsRow.getPayload(), JobAnalyticsEvent.class);
        var lifecycle = objectMapper.readValue(lifecycleRow.getPayload(), JobLifecycleEvent.class);
        assertThat(analytics.eventId()).isEqualTo(analyticsRow.getEventId());
        assertThat(analytics.eventType()).isEqualTo(type);
        assertThat(analytics.timestamp()).isCloseTo(analyticsRow.getCreatedAt(), within(1, ChronoUnit.MICROS));
        assertThat(analytics.data().jobId()).isEqualTo(job.getId());
        assertThat(analytics.data().clientId()).isEqualTo(clientId);
        assertThat(analytics.data().status()).isEqualTo(status);
        assertThat(analytics.data().skill()).isEqualTo("PLUMBING");
        assertThat(analytics.data().city()).isEqualTo("CASABLANCA");
        assertThat(analytics.data().createdAt()).isCloseTo(job.getCreatedAt(), within(1, ChronoUnit.MICROS));
        assertThat(lifecycle.eventId()).isEqualTo(analytics.eventId());
        assertThat(lifecycle.eventType()).isEqualTo(analytics.eventType());
        assertThat(lifecycle.timestamp()).isEqualTo(analytics.timestamp());
        assertThat(lifecycle.data()).isEqualTo(analytics.data());
        assertThat(lifecycle.jobVersion()).isEqualTo(version);
        assertThat(lifecycle.assignedWorkerId()).isEqualTo(assignedWorker);

        var publicJson = objectMapper.readTree(analyticsRow.getPayload());
        assertThat(publicJson.propertyNames()).containsExactlyInAnyOrder("eventId", "eventType", "timestamp", "data");
        assertThat(publicJson.get("data").propertyNames())
                .containsExactlyInAnyOrder("jobId", "clientId", "status", "skill", "city", "createdAt");
    }

    private Job createJob() {
        return jobService.create(clientId, request());
    }

    private CreateJobRequest request() {
        return new CreateJobRequest("Fix the sink", "Private description", "PLUMBING", "CASABLANCA",
                "Private address", BigDecimal.TEN, EstimatedDuration.HALF_DAY, null);
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
        @Bean SimpleMeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
        @Bean JobImageProperties jobImageProperties() { return new JobImageProperties(); }
    }
}
