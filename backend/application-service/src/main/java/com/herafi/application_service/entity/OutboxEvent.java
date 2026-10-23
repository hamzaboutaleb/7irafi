package com.herafi.application_service.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.herafi.application_service.event.ApplicationAnalyticsEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable event snapshot with delivery state tracked independently per destination. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_events", uniqueConstraints = @UniqueConstraint(
        name = "uq_outbox_event_destination", columnNames = {"event_id", "destination"}))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private ApplicationAnalyticsEvent.Type eventType;

    @Column(nullable = false, updatable = false, length = 120)
    private String destination;

    @Column(nullable = false, updatable = false)
    private UUID applicationId;

    @Column(nullable = false, updatable = false)
    private long applicationVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    public OutboxEvent(UUID eventId, ApplicationAnalyticsEvent.Type eventType, String destination,
            UUID applicationId, long applicationVersion, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.destination = destination;
        this.applicationId = applicationId;
        this.applicationVersion = applicationVersion;
        this.payload = payload;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public void markPublished(Instant acknowledgedAt) {
        attempts++;
        publishedAt = acknowledgedAt;
        lastError = null;
    }

    public void recordFailure(Instant retryAt, String error) {
        attempts++;
        nextAttemptAt = retryAt;
        lastError = error;
    }
}
