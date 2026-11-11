package com.herafi.analytics.listener;

import java.time.Instant;
import java.util.UUID;

import com.herafi.analytics.service.ProjectionService.Invalidation;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class InvalidationPublisher {
    private static final Logger log = LoggerFactory.getLogger(InvalidationPublisher.class);
    private final SimpMessagingTemplate messaging;
    public InvalidationPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener
    public void afterCommit(Invalidation event) {
        try {
            messaging.convertAndSendToUser(event.userId().toString(), "/queue/analytics",
                    new Message(event.eventId(), event.resource(), event.occurredAt()));
        } catch (RuntimeException e) {
            log.warn("Unable to send analytics invalidation for event {}", event.eventId(), e);
        }
    }

    public record Message(UUID eventId, String resource, Instant occurredAt) { }
}
