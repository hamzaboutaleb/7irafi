package com.herafi.analytics;

import java.time.Instant;
import java.util.UUID;

import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.dto.JobStatus;

final class EventFixtures {
    static final Instant CREATED_AT = Instant.parse("2026-04-30T23:30:00Z");
    static final Instant OCCURRED_AT = Instant.parse("2026-05-01T01:01:00Z");

    private EventFixtures() { }

    static JobEvent job(UUID owner, UUID jobId, JobEvent.Type type, JobStatus status) {
        return new JobEvent(UUID.randomUUID(), type, OCCURRED_AT,
                new JobEvent.Data(jobId, owner, status, "PLUMBING", "CASABLANCA", CREATED_AT));
    }

    static ApplicationEvent application(UUID owner, UUID applicationId, UUID jobId, ApplicationEvent.Type type) {
        return new ApplicationEvent(UUID.randomUUID(), type, OCCURRED_AT,
                new ApplicationEvent.Data(applicationId, jobId, owner, type.status(),
                        "PLUMBING", "CASABLANCA", CREATED_AT));
    }
}
