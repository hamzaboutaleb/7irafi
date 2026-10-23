package com.herafi.application_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_job_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedJobEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private Instant processedAt;

    public ProcessedJobEvent(UUID eventId, UUID jobId) {
        this.eventId = eventId;
        this.jobId = jobId;
        this.processedAt = Instant.now();
    }
}
