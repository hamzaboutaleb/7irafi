package com.herafi.analytics.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.Assert;

import static java.util.Objects.requireNonNull;

public record ApplicationEvent(UUID eventId, Type eventType, Instant timestamp, Data data) implements AnalyticsEvent {
    public ApplicationEvent {
        requireNonNull(eventId, "eventId");
        requireNonNull(eventType, "eventType");
        requireNonNull(timestamp, "timestamp");
        requireNonNull(data, "data");
        if (data.status() != eventType.status()) {
            throw new IllegalArgumentException(eventType + " requires status " + eventType.status());
        }
    }

    public enum Type {
        APPLICATION_SUBMITTED(ApplicationStatus.PENDING),
        APPLICATION_ACCEPTED(ApplicationStatus.ACCEPTED),
        APPLICATION_REJECTED(ApplicationStatus.REJECTED);

        private final ApplicationStatus status;

        Type(ApplicationStatus status) {
            this.status = status;
        }

        public ApplicationStatus status() {
            return status;
        }
    }

    public record Data(UUID applicationId, UUID jobId, UUID applicantId, ApplicationStatus status,
                       String jobSkill, String jobCity, Instant submittedAt) {
        public Data {
            requireNonNull(applicationId, "applicationId");
            requireNonNull(jobId, "jobId");
            requireNonNull(applicantId, "applicantId");
            requireNonNull(status, "status");
            requireNonNull(submittedAt, "submittedAt");
            Assert.hasText(jobSkill, "jobSkill must be a non-blank reference code");
            Assert.hasText(jobCity, "jobCity must be a non-blank reference code");
        }
    }
}
