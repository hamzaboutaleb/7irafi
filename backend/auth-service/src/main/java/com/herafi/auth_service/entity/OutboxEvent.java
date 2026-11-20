package com.herafi.auth_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// One message waiting to be sent to Kafka (table created by V2__create_outbox_events.sql).
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false, length = 120)
    private String topic;

    @Column(nullable = false, updatable = false, length = 120)
    private String messageKey;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // empty until Kafka confirms it received the message
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    // a new message, ready to send right away
    public OutboxEvent(String topic, String messageKey, String payload) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    // Kafka confirmed it: never send it again
    public void markPublished(Instant sentAt) {
        attempts++;
        publishedAt = sentAt;
        lastError = null;
    }

    // sending failed: try again later
    public void recordFailure(Instant retryAt, String error) {
        attempts++;
        nextAttemptAt = retryAt;
        lastError = error;
    }
}
