package com.herafi.job_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.herafi.job_service.entity.OutboxEvent;
import com.herafi.job_service.event.JobAnalyticsEvent;
import com.herafi.job_service.repository.OutboxEventRepository;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(OutboxEventDispatcher.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxEventPublisherIntegrationTests {

    @Autowired private OutboxEventDispatcher dispatcher;
    @Autowired private OutboxEventRepository events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private KafkaTemplate<String, String> kafka;

    private final UUID jobId = UUID.randomUUID();
    private final Instant createdAt = Instant.now().minusSeconds(30);

    @BeforeEach
    void resetDatabase() {
        events.deleteAllInBatch();
        reset(kafka);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void anEmptyQueueDoesNotContactKafka() {
        assertThat(dispatcher.publishNext()).isFalse();
        verifyNoInteractions(kafka);
    }

    @Test
    void sendsTheSavedJsonAndJobKeyToBothDestinationsAndDoesNotResendPublishedRows() {
        OutboxEvent analytics = save(jobId, 0, "analytics.events");
        OutboxEvent lifecycle = save(jobId, 0, "jobs.lifecycle");

        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(dispatcher.publishNext()).isFalse();

        for (OutboxEvent event : events.findAll()) {
            assertThat(event.getPublishedAt()).isNotNull();
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getLastError()).isNull();
        }
        verify(kafka).send("analytics.events", jobId.toString(), analytics.getPayload());
        verify(kafka).send("jobs.lifecycle", jobId.toString(), lifecycle.getPayload());
    }

    @Test
    void jobVersionDeterminesOrderEvenWhenCreationTimesAreEqualAndVersionsHaveGaps() {
        OutboxEvent newer = save(jobId, 5, "analytics.events");
        OutboxEvent older = save(jobId, 0, "analytics.events");

        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(reload(older).getPublishedAt()).isNotNull();
        assertThat(reload(newer).getPublishedAt()).isNull();
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(reload(newer).getPublishedAt()).isNotNull();
    }

    @Test
    void failedSendsBlockNewerVersionsDuringBackoffAndRetryTheSameSnapshot() {
        OutboxEvent first = save(jobId, 0, "analytics.events");
        OutboxEvent next = save(jobId, 2, "analytics.events");
        OutboxEvent lifecycle = save(jobId, 0, "jobs.lifecycle");
        makeDue(first);
        when(kafka.send("analytics.events", jobId.toString(), first.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));
        Instant beforeAttempt = Instant.now();

        assertThat(dispatcher.publishNext()).isFalse();

        OutboxEvent failed = reload(first);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("Broker unavailable");
        assertThat(failed.getNextAttemptAt()).isAfterOrEqualTo(beforeAttempt.plusSeconds(1));
        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(reload(lifecycle).getPublishedAt()).isNotNull();
        assertThat(dispatcher.publishNext()).isFalse();
        assertThat(reload(next).getPublishedAt()).isNull();

        makeDue(first);
        when(kafka.send("analytics.events", jobId.toString(), first.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThat(dispatcher.publishNext()).isTrue();
        assertThat(reload(first).getEventId()).isEqualTo(first.getEventId());
        assertThat(reload(first).getPayload()).isEqualTo(first.getPayload());
        assertThat(reload(first).getAttempts()).isEqualTo(2);
        assertThat(reload(first).getLastError()).isNull();
        assertThat(dispatcher.publishNext()).isTrue();
        verify(kafka, times(2)).send("analytics.events", jobId.toString(), first.getPayload());
    }

    @Test
    void synchronousProducerFailuresAreSavedAndBackoffIsCapped() {
        OutboxEvent event = save(jobId, 0, "analytics.events");
        jdbc.update("UPDATE outbox_events SET attempts = 20 WHERE id = ?", event.getId());
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Metadata unavailable"));
        Instant beforeAttempt = Instant.now();

        assertThat(dispatcher.publishNext()).isFalse();

        OutboxEvent failed = reload(event);
        assertThat(failed.getPublishedAt()).isNull();
        assertThat(failed.getAttempts()).isEqualTo(21);
        assertThat(failed.getNextAttemptAt()).isBetween(
                beforeAttempt.plusSeconds(60), Instant.now().plusSeconds(60));
        assertThat(failed.getLastError()).contains("Metadata unavailable");
    }

    @Test
    void uncommittedAndRolledBackEventsAreNeverSent() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions().executeWithoutResult(transaction -> {
                save(jobId, 0, "analytics.events");
                try {
                    assertThat(executor.submit(dispatcher::publishNext).get(5, TimeUnit.SECONDS)).isFalse();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                transaction.setRollbackOnly();
            });
        }

        assertThat(events.count()).isZero();
        assertThat(dispatcher.publishNext()).isFalse();
        verifyNoInteractions(kafka);
    }

    @Test
    void concurrentPublishersSkipTheLockedJobAndWaitForAcknowledgementBeforeAdvancing() throws Exception {
        OutboxEvent first = save(jobId, 0, "analytics.events");
        OutboxEvent next = save(jobId, 2, "analytics.events");
        CompletableFuture<SendResult<String, String>> acknowledgement = new CompletableFuture<>();
        CountDownLatch sending = new CountDownLatch(1);
        when(kafka.send("analytics.events", jobId.toString(), first.getPayload())).thenAnswer(call -> {
            sending.countDown();
            return acknowledgement;
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstDelivery = executor.submit(dispatcher::publishNext);
            try {
                assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(reload(first).getPublishedAt()).isNull();
                assertThat(firstDelivery.isDone()).isFalse();
                assertThat(executor.submit(dispatcher::publishNext).get(5, TimeUnit.SECONDS)).isFalse();

                OutboxEvent unrelated = save(UUID.randomUUID(), 0, "analytics.events");
                assertThat(executor.submit(dispatcher::publishNext).get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(reload(unrelated).getPublishedAt()).isNotNull();
                assertThat(reload(next).getPublishedAt()).isNull();
            } finally {
                acknowledgement.complete(null);
            }
            assertThat(firstDelivery.get(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(dispatcher.publishNext()).isTrue();
        verify(kafka).send("analytics.events", jobId.toString(), first.getPayload());
        verify(kafka).send("analytics.events", jobId.toString(), next.getPayload());
    }

    @Test
    void aDatabaseFailureAfterKafkaAcknowledgesLeavesTheOriginalEventAvailableForReplay() {
        OutboxEvent event = save(jobId, 0, "analytics.events");
        when(kafka.send(anyString(), anyString(), anyString())).thenAnswer(call -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("Simulated database commit failure");
                }
            });
            return CompletableFuture.completedFuture(null);
        });

        assertThatThrownBy(dispatcher::publishNext).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database commit failure");
        assertThat(reload(event).getPublishedAt()).isNull();
        assertThat(reload(event).getAttempts()).isZero();
        doReturn(CompletableFuture.completedFuture(null)).when(kafka)
                .send(anyString(), anyString(), anyString());

        assertThat(dispatcher.publishNext()).isTrue();
        verify(kafka, times(2)).send("analytics.events", jobId.toString(), event.getPayload());
    }

    private OutboxEvent save(UUID id, long version, String destination) {
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"jobId\":\"" + id
                + "\",\"jobVersion\":" + version + "}";
        return reload(events.saveAndFlush(new OutboxEvent(eventId, JobAnalyticsEvent.Type.JOB_CREATED,
                destination, id, version, payload, createdAt)));
    }

    private OutboxEvent reload(OutboxEvent event) {
        return events.findById(event.getId()).orElseThrow();
    }

    private void makeDue(OutboxEvent event) {
        jdbc.update("UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?",
                Timestamp.from(createdAt.minusSeconds(1)), event.getId());
    }

    private TransactionTemplate transactions() {
        return new TransactionTemplate(transactionManager);
    }
}
