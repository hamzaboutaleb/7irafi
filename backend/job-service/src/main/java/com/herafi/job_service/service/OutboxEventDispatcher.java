package com.herafi.job_service.service;

import java.time.Instant;
import java.util.concurrent.CompletionException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.job_service.entity.OutboxEvent;
import com.herafi.job_service.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventDispatcher {

    private final OutboxEventRepository events;
    private final KafkaTemplate<String, String> kafka;

    /** Each delivery owns its row lock and commit, independently of the scheduler's batch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishNext() {
        OutboxEvent event = events.lockNextPending(Instant.now()).orElse(null);
        if (event == null) {
            return false;
        }

        try {
            kafka.send(event.getDestination(), event.getJobId().toString(), event.getPayload()).join();
        } catch (RuntimeException failure) {
            long delaySeconds = Math.min(60L, 1L << Math.min(event.getAttempts(), 6));
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            String error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            event.recordFailure(Instant.now().plusSeconds(delaySeconds),
                    error.substring(0, Math.min(error.length(), 2000)));
            log.warn("Outbox delivery failed: eventId={}, destination={}, attempts={}, nextAttemptAt={}",
                    event.getEventId(), event.getDestination(), event.getAttempts(),
                    event.getNextAttemptAt(), failure);
            return false;
        }

        event.markPublished(Instant.now());
        return true;
    }
}
