package com.herafi.application_service.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Consumer-owned DTO matching job-service's internal jobs.lifecycle contract. */
public record JobLifecycleEvent(
        UUID eventId,
        Type eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        Data data,
        UUID assignedWorkerId,
        Long jobVersion) {

    public JobLifecycleEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(jobVersion, "jobVersion");
        if (jobVersion < 0) {
            throw new IllegalArgumentException("jobVersion must be non-negative");
        }
        if (eventType == Type.JOB_CREATED && data.status() != Status.OPEN) {
            throw new IllegalArgumentException("JOB_CREATED requires OPEN status");
        }
        if (eventType == Type.JOB_STATUS_CHANGED && data.status() == Status.OPEN) {
            throw new IllegalArgumentException("JOB_STATUS_CHANGED requires ASSIGNED, COMPLETED or CANCELLED status");
        }
        boolean assigned = data.status() == Status.ASSIGNED || data.status() == Status.COMPLETED;
        if (assigned != (assignedWorkerId != null)) {
            throw new IllegalArgumentException("Only ASSIGNED and COMPLETED jobs require an assignedWorkerId");
        }
    }

    public enum Type {
        JOB_CREATED, JOB_STATUS_CHANGED, JOB_DELETED
    }

    public enum Status {
        OPEN, ASSIGNED, COMPLETED, CANCELLED
    }

    public record Data(
            UUID jobId,
            UUID clientId,
            Status status,
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
