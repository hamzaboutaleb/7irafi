package com.herafi.job_service.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.herafi.job_service.entity.JobStatus;

/** Internal job snapshot for application-service; never sent to analytics.events. */
public record JobLifecycleEvent(
        UUID eventId,
        JobAnalyticsEvent.Type eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
        JobAnalyticsEvent.Data data,
        UUID assignedWorkerId,
        Long jobVersion) {

    public JobLifecycleEvent {
        new JobAnalyticsEvent(eventId, eventType, timestamp, data);
        Objects.requireNonNull(jobVersion, "jobVersion");
        if (jobVersion < 0) {
            throw new IllegalArgumentException("jobVersion must be non-negative");
        }
        boolean assigned = data.status() == JobStatus.ASSIGNED
                || data.status() == JobStatus.COMPLETED;
        if (assigned != (assignedWorkerId != null)) {
            throw new IllegalArgumentException("Only ASSIGNED and COMPLETED jobs require an assignedWorkerId");
        }
    }
}
