package com.herafi.rating_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.rating_service.entity.OutboxEvent;
import com.herafi.rating_service.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Every few seconds: send the waiting outbox messages to Kafka and mark them sent.
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    // wait at most this long for Kafka to confirm one message
    private static final long SEND_TIMEOUT_SECONDS = 10;
    // never wait more than 5 minutes between two tries
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;

    // runs again 2 seconds after the previous run has finished
    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
    @Transactional
    public void sendPending() {
        List<OutboxEvent> pending = repository
                .findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now());

        for (OutboxEvent event : pending) {
            send(event);
        }
    }

    // send one message and wait for Kafka to say "received"
    private void send(OutboxEvent event) {
        try {
            kafka.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markPublished(Instant.now());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            event.recordFailure(nextTry(event), "interrupted");
        } catch (Exception exception) {
            event.recordFailure(nextTry(event), exception.toString());
            log.warn("Outbox send failed: id={}, topic={}, attempts={}",
                    event.getId(), event.getTopic(), event.getAttempts(), exception);
        }
    }

    // wait 1s, 2s, 4s, 8s ... between tries, up to 5 minutes
    private Instant nextTry(OutboxEvent event) {
        long seconds = 1L << Math.min(event.getAttempts(), 9);
        Duration delay = Duration.ofSeconds(seconds);
        if (delay.compareTo(MAX_RETRY_DELAY) > 0) {
            delay = MAX_RETRY_DELAY;
        }
        return Instant.now().plus(delay);
    }
}
