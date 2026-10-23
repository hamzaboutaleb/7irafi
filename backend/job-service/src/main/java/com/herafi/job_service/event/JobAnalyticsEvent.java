package com.herafi.job_service.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.herafi.job_service.entity.JobStatus;

/** Public analytics contract; excludes private job details and assignment metadata. */
public record JobAnalyticsEvent(
        UUID eventId,
        Type eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        Data data) {

    public JobAnalyticsEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(data, "data");
        if (eventType == Type.JOB_CREATED && data.status() != JobStatus.OPEN) {
            throw new IllegalArgumentException("JOB_CREATED requires OPEN status");
        }
        if (eventType == Type.JOB_STATUS_CHANGED && data.status() == JobStatus.OPEN) {
            throw new IllegalArgumentException("JOB_STATUS_CHANGED requires ASSIGNED, COMPLETED or CANCELLED status");
        }
    }

    public enum Type {
        JOB_CREATED, JOB_STATUS_CHANGED, JOB_DELETED
    }

    public record Data(
            UUID jobId,
            UUID clientId,
            JobStatus status,
            String skill,
            String city,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt) {

        public Data {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(createdAt, "createdAt");
            if (skill == null || skill.isBlank() || city == null || city.isBlank()) {
                throw new IllegalArgumentException("skill and city must be non-blank reference codes");
            }
        }
    }
}
