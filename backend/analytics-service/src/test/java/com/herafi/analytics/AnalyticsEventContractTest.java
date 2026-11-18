package com.herafi.analytics;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.ApplicationStatus;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.dto.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void decodesTheProducerJobFixture() throws Exception {
        var event = (JobEvent) mapper.readValue(fixture("job-created.json"), AnalyticsEvent.class);

        assertThat(event.eventType()).isEqualTo(JobEvent.Type.JOB_CREATED);
        assertThat(event.data().status()).isEqualTo(JobStatus.OPEN);
        assertThat(event.data().createdAt()).isEqualTo(Instant.parse("2026-04-30T23:30:00Z"));
        assertThat(event.timestamp()).isNotEqualTo(event.data().createdAt());
    }

    @Test
    void decodesTheProducerApplicationFixture() throws Exception {
        var event = (ApplicationEvent) mapper.readValue(fixture("application-submitted.json"), AnalyticsEvent.class);

        assertThat(event.eventType()).isEqualTo(ApplicationEvent.Type.APPLICATION_SUBMITTED);
        assertThat(event.data().status()).isEqualTo(ApplicationStatus.PENDING);
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
