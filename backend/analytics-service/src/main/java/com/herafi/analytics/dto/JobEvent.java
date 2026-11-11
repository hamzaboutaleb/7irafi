package com.herafi.analytics.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.Assert;

import static java.util.Objects.requireNonNull;

public record JobEvent(UUID eventId, Type eventType, Instant timestamp, Data data) implements AnalyticsEvent {
    public JobEvent {
        requireNonNull(eventId, "eventId");
        requireNonNull(eventType, "eventType");
        requireNonNull(timestamp, "timestamp");
        requireNonNull(data, "data");
        if (!eventType.accepts(data.status())) {
            throw new IllegalArgumentException(eventType + " does not accept status " + data.status());
        }
    }

    public enum Type {
        JOB_CREATED, JOB_STATUS_CHANGED, JOB_DELETED;

        boolean accepts(JobStatus status) {
            return switch (this) {
                case JOB_CREATED -> status == JobStatus.OPEN;
                case JOB_STATUS_CHANGED -> status != JobStatus.OPEN;
                case JOB_DELETED -> true;
            };
        }
    }

    public record Data(UUID jobId, UUID clientId, JobStatus status, String skill, String city, Instant createdAt) {
        public Data {
            requireNonNull(jobId, "jobId");
            requireNonNull(clientId, "clientId");
            requireNonNull(status, "status");
            requireNonNull(createdAt, "createdAt");
            Assert.hasText(skill, "skill must be a non-blank reference code");
            Assert.hasText(city, "city must be a non-blank reference code");
        }
    }
}
