package com.herafi.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import com.herafi.auth_service.entity.OutboxEvent;
import com.herafi.auth_service.repository.OutboxEventRepository;

class OutboxRelayTests {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(repository, kafka);

    // Kafka says "received": the message is marked sent
    @Test
    void marksMessageSentWhenKafkaConfirms() {
        OutboxEvent event = new OutboxEvent("users.registered", "user-1", "{\"a\":1}");
        when(repository.findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(event));
        when(kafka.send("users.registered", "user-1", "{\"a\":1}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.sendPending();

        verify(kafka).send("users.registered", "user-1", "{\"a\":1}");
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    // Kafka fails: the message stays unsent and is retried later
    @Test
    void schedulesRetryWhenKafkaFails() {
        OutboxEvent event = new OutboxEvent("users.registered", "user-1", "{}");
        when(repository.findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(event));
        when(kafka.send("users.registered", "user-1", "{}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka down")));

        relay.sendPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(event.getLastError()).contains("Kafka down");
    }
}
