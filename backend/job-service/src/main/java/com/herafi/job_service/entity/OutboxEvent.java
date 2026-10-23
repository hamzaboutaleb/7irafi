package com.herafi.job_service.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.herafi.job_service.event.JobAnalyticsEvent;

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
    private JobAnalyticsEvent.Type eventType;

    @Column(nullable = false, updatable = false, length = 120)
    private String destination;

    @Column(nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, updatable = false)
    private long jobVersion;

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

    public OutboxEvent(UUID eventId, JobAnalyticsEvent.Type eventType, String destination,
            UUID jobId, long jobVersion, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.destination = destination;
        this.jobId = jobId;
        this.jobVersion = jobVersion;
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
