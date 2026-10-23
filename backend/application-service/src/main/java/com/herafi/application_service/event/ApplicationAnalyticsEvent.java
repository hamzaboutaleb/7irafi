package com.herafi.application_service.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Analytics statuses are independent of the existing application's HTTP status labels. */
public record ApplicationAnalyticsEvent(
        UUID eventId,
        Type eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        Data data) {

    public ApplicationAnalyticsEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(data, "data");
        Status expected = switch (eventType) {
            case APPLICATION_SUBMITTED -> Status.PENDING;
            case APPLICATION_ACCEPTED -> Status.ACCEPTED;
            case APPLICATION_REJECTED -> Status.REJECTED;
        };
        if (data.status() != expected) {
            throw new IllegalArgumentException(eventType + " requires " + expected + " status");
        }
    }

    public enum Type {
        APPLICATION_SUBMITTED, APPLICATION_ACCEPTED, APPLICATION_REJECTED
    }

    public enum Status {
        PENDING, ACCEPTED, REJECTED
    }

    public record Data(
            UUID applicationId,
            UUID jobId,
            UUID applicantId,
            String jobSkill,
            String jobCity,
            Status status,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant submittedAt) {

        public Data {
            Objects.requireNonNull(applicationId, "applicationId");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(applicantId, "applicantId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(submittedAt, "submittedAt");
            if (jobSkill == null || jobSkill.isBlank() || jobCity == null || jobCity.isBlank()) {
                throw new IllegalArgumentException("jobSkill and jobCity must be non-blank reference codes");
            }
        }
    }
}
