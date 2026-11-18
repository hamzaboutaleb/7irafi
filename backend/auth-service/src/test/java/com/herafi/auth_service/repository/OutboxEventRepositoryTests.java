package com.herafi.auth_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.entity.OutboxEvent;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class OutboxEventRepositoryTests {

    @Autowired
    private OutboxEventRepository repository;

    // only messages that are not sent yet and due now come back
    @Test
    void findsOnlyPendingMessagesThatAreDue() {
        OutboxEvent pending = repository.save(new OutboxEvent("users.registered", "key-1", "{}"));

        OutboxEvent sent = new OutboxEvent("users.registered", "key-2", "{}");
        sent.markPublished(Instant.now());
        repository.save(sent);

        OutboxEvent waiting = new OutboxEvent("users.registered", "key-3", "{}");
        waiting.recordFailure(Instant.now().plusSeconds(60), "Kafka down");
        repository.save(waiting);

        List<OutboxEvent> due = repository
                .findTop50ByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now());

        assertThat(due).extracting(OutboxEvent::getId).containsExactly(pending.getId());
    }
}
