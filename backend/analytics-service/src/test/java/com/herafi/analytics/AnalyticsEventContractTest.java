package com.herafi.analytics;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @CsvSource({"JOB_CREATED,OPEN", "JOB_STATUS_CHANGED,ASSIGNED", "JOB_STATUS_CHANGED,COMPLETED",
            "JOB_STATUS_CHANGED,CANCELLED", "JOB_DELETED,OPEN"})
    void decodesPublicJobSnapshots(String eventType, String status) throws Exception {
        String json = fixture("job-created.json").replace("JOB_CREATED", eventType).replace("OPEN", status);
        var event = (JobEvent) mapper.readValue(json, AnalyticsEvent.class);

        assertThat(event.eventType().name()).isEqualTo(eventType);
        assertThat(event.data().status().name()).isEqualTo(status);
        assertThat(event.data().createdAt()).isEqualTo(Instant.parse("2026-04-30T23:30:00Z"));
        assertThat(event.timestamp()).isNotEqualTo(event.data().createdAt());
    }

    @ParameterizedTest
    @CsvSource({"APPLICATION_SUBMITTED,PENDING", "APPLICATION_ACCEPTED,ACCEPTED", "APPLICATION_REJECTED,REJECTED"})
    void decodesPublicApplicationSnapshots(String eventType, String status) throws Exception {
        String json = fixture("application-submitted.json").replace("APPLICATION_SUBMITTED", eventType).replace("PENDING", status);
        var event = (ApplicationEvent) mapper.readValue(json, AnalyticsEvent.class);

        assertThat(event.eventType().name()).isEqualTo(eventType);
        assertThat(event.data().status().name()).isEqualTo(status);
        assertThat(event.data().submittedAt()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
    }

    @Test
    void rejectsUnknownTypesAndIncompletePayloadsBeforeProjection() throws Exception {
        String json = fixture("job-created.json");
        assertThatThrownBy(() -> mapper.readValue(json.replace("JOB_CREATED", "OTHER"), AnalyticsEvent.class))
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue(json.replace("\"PLUMBING\"", "null"), AnalyticsEvent.class))
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue(json.replace("OPEN", "ASSIGNED"), AnalyticsEvent.class))
                .isInstanceOf(JsonMappingException.class);
    }

    private String fixture(String name) throws Exception {
        return new ClassPathResource("events/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
