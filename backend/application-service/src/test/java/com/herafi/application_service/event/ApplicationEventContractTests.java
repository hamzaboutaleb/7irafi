package com.herafi.application_service.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ApplicationEventContractTests {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID eventId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID jobId = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private final UUID applicantId = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private final UUID applicationId = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private final Instant occurredAt = Instant.parse("2026-10-18T19:07:00Z");

    @Test
    void allApplicationEventsMatchThePublicJsonContract() {
        for (var type : ApplicationAnalyticsEvent.Type.values()) {
            var status = switch (type) {
                case APPLICATION_SUBMITTED -> ApplicationAnalyticsEvent.Status.PENDING;
                case APPLICATION_ACCEPTED -> ApplicationAnalyticsEvent.Status.ACCEPTED;
                case APPLICATION_REJECTED -> ApplicationAnalyticsEvent.Status.REJECTED;
            };
            var data = new ApplicationAnalyticsEvent.Data(applicationId, jobId, applicantId,
                    "PLUMBING", "CASABLANCA", status, occurredAt);
            var event = new ApplicationAnalyticsEvent(eventId, type, occurredAt, data);
            String json = mapper.writeValueAsString(event);
            var root = mapper.readTree(json);

            assertThat(root.propertyNames()).containsExactlyInAnyOrder("eventId", "eventType", "timestamp", "data");
            assertThat(root.get("eventId").asString()).isEqualTo(eventId.toString());
            assertThat(root.get("eventType").asString()).isEqualTo(type.name());
            assertThat(root.get("timestamp").asString()).isEqualTo("2026-10-18T19:07:00Z");
            var payload = root.get("data");
            assertThat(payload.propertyNames()).containsExactlyInAnyOrder(
                    "applicationId", "jobId", "applicantId", "jobSkill", "jobCity", "status", "submittedAt");
            assertThat(payload.get("applicationId").asString()).isEqualTo(applicationId.toString());
            assertThat(payload.get("jobId").asString()).isEqualTo(jobId.toString());
            assertThat(payload.get("applicantId").asString()).isEqualTo(applicantId.toString());
            assertThat(payload.get("jobSkill").asString()).isEqualTo("PLUMBING");
            assertThat(payload.get("jobCity").asString()).isEqualTo("CASABLANCA");
            assertThat(payload.get("status").asString()).isEqualTo(status.name());
            assertThat(payload.get("submittedAt").asString()).isEqualTo("2026-10-18T19:07:00Z");
            assertThat(mapper.readValue(json, ApplicationAnalyticsEvent.class)).isEqualTo(event);
        }
    }

    @Test
    void consumesTheInternalAssignedJobContract() {
        String json = """
                {
                  "eventId": "11111111-1111-4111-8111-111111111111",
                  "eventType": "JOB_STATUS_CHANGED",
                  "timestamp": "2026-10-18T19:07:00Z",
                  "data": {
                    "jobId": "22222222-2222-4222-8222-222222222222",
                    "clientId": "33333333-3333-4333-8333-333333333333",
                    "status": "ASSIGNED",
                    "skill": "PLUMBING",
                    "city": "CASABLANCA",
                    "createdAt": "2026-10-15T08:30:00Z"
                  },
                  "assignedWorkerId": "44444444-4444-4444-8444-444444444444",
                  "jobVersion": 3
                }
                """;
        var event = mapper.readValue(json, JobLifecycleEvent.class);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo(JobLifecycleEvent.Type.JOB_STATUS_CHANGED);
        assertThat(event.timestamp()).isEqualTo(occurredAt);
        assertThat(event.data().jobId()).isEqualTo(jobId);
        assertThat(event.data().status()).isEqualTo(JobLifecycleEvent.Status.ASSIGNED);
        assertThat(event.data().skill()).isEqualTo("PLUMBING");
        assertThat(event.data().city()).isEqualTo("CASABLANCA");
        assertThat(event.data().createdAt()).isEqualTo(Instant.parse("2026-10-15T08:30:00Z"));
        assertThat(event.assignedWorkerId()).isEqualTo(applicantId);
        assertThat(event.jobVersion()).isEqualTo(3L);
        assertThat(mapper.readTree(mapper.writeValueAsString(event))).isEqualTo(mapper.readTree(json));
    }

    @Test
    void consumesCancellationWithNoAssignedWorker() {
        var data = new JobLifecycleEvent.Data(jobId, UUID.randomUUID(), JobLifecycleEvent.Status.CANCELLED,
                "PLUMBING", "CASABLANCA", occurredAt);
        var event = new JobLifecycleEvent(eventId, JobLifecycleEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data, null, 4L);

        assertThat(mapper.readValue(mapper.writeValueAsString(event), JobLifecycleEvent.class)).isEqualTo(event);
    }

    @Test
    void rejectsMismatchedApplicationStatuses() {
        var data = new ApplicationAnalyticsEvent.Data(applicationId, jobId, applicantId,
                "PLUMBING", "CASABLANCA", ApplicationAnalyticsEvent.Status.PENDING, occurredAt);

        assertThatThrownBy(() -> new ApplicationAnalyticsEvent(eventId,
                ApplicationAnalyticsEvent.Type.APPLICATION_ACCEPTED, occurredAt, data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIncompleteOrUnversionedAssignmentEvents() {
        var data = new JobLifecycleEvent.Data(jobId, UUID.randomUUID(), JobLifecycleEvent.Status.ASSIGNED,
                "PLUMBING", "CASABLANCA", occurredAt);

        assertThatThrownBy(() -> new JobLifecycleEvent(eventId, JobLifecycleEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data, null, 3L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JobLifecycleEvent(eventId, JobLifecycleEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data, applicantId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JobLifecycleEvent(eventId, JobLifecycleEvent.Type.JOB_STATUS_CHANGED,
                occurredAt, data, applicantId, -1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
