package com.herafi.job_service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.herafi.job_service.entity.JobStatus;

import tools.jackson.databind.json.JsonMapper;

class JobEventContractTests {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID eventId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID jobId = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private final UUID clientId = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private final UUID workerId = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private final Instant occurredAt = Instant.parse("2026-10-18T19:07:00Z");
    private final Instant createdAt = Instant.parse("2026-10-15T08:30:00Z");

    @Test
    void analyticsEventsMatchThePublicJsonContractWithoutExtraJobFields() {
        for (var type : JobAnalyticsEvent.Type.values()) {
            var status = type == JobAnalyticsEvent.Type.JOB_CREATED
                    ? JobStatus.OPEN : JobStatus.COMPLETED;
            var event = new JobAnalyticsEvent(eventId, type, occurredAt, data(status));
            String json = mapper.writeValueAsString(event);
            var root = mapper.readTree(json);

            assertThat(root.propertyNames()).containsExactlyInAnyOrder("eventId", "eventType", "timestamp", "data");
            assertThat(root.get("eventId").asString()).isEqualTo(eventId.toString());
            assertThat(root.get("eventType").asString()).isEqualTo(type.name());
            assertThat(root.get("timestamp").asString()).isEqualTo("2026-10-18T19:07:00Z");
            var payload = root.get("data");
            assertThat(payload.propertyNames())
                    .containsExactlyInAnyOrder("jobId", "clientId", "status", "skill", "city", "createdAt");
            assertThat(payload.get("jobId").asString()).isEqualTo(jobId.toString());
            assertThat(payload.get("clientId").asString()).isEqualTo(clientId.toString());
            assertThat(payload.get("status").asString()).isEqualTo(status.name());
            assertThat(payload.get("skill").asString()).isEqualTo("PLUMBING");
            assertThat(payload.get("city").asString()).isEqualTo("CASABLANCA");
            assertThat(payload.get("createdAt").asString()).isEqualTo("2026-10-15T08:30:00Z");
            assertThat(mapper.readValue(json, JobAnalyticsEvent.class)).isEqualTo(event);
        }
    }

    @Test
    void internalLifecycleAddsAssignmentAndVersionWithoutChangingTheAnalyticsPayload() {
        var event = new JobLifecycleEvent(eventId, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data(JobStatus.ASSIGNED), workerId, 3L);
        String json = mapper.writeValueAsString(event);
        var root = mapper.readTree(json);

        assertThat(root.propertyNames())
                .containsExactlyInAnyOrder("eventId", "eventType", "timestamp", "data", "assignedWorkerId", "jobVersion");
        assertThat(root.get("assignedWorkerId").asString()).isEqualTo(workerId.toString());
        assertThat(root.get("jobVersion").asLong()).isEqualTo(3L);
        assertThat(root.get("data").propertyNames())
                .containsExactlyInAnyOrder("jobId", "clientId", "status", "skill", "city", "createdAt");
        assertThat(mapper.readValue(json, JobLifecycleEvent.class)).isEqualTo(event);
    }

    @Test
    void rejectsEventTypesThatDisagreeWithTheSnapshotStatus() {
        assertThatThrownBy(() -> new JobAnalyticsEvent(eventId, JobAnalyticsEvent.Type.JOB_CREATED,
                occurredAt, data(JobStatus.ASSIGNED))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobAnalyticsEvent(eventId, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data(JobStatus.OPEN))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycleRequiresTheAssignmentForAssignedJobsAndANonNegativeVersion() {
        assertThatThrownBy(() -> new JobLifecycleEvent(eventId, JobAnalyticsEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data(JobStatus.ASSIGNED), null, 3L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobLifecycleEvent(eventId, JobAnalyticsEvent.Type.JOB_CREATED,
                occurredAt, data(JobStatus.OPEN), workerId, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobLifecycleEvent(eventId, JobAnalyticsEvent.Type.JOB_CREATED,
                occurredAt, data(JobStatus.OPEN), null, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JobAnalyticsEvent.Data data(JobStatus status) {
        return new JobAnalyticsEvent.Data(jobId, clientId, status, "PLUMBING", "CASABLANCA", createdAt);
    }
}
