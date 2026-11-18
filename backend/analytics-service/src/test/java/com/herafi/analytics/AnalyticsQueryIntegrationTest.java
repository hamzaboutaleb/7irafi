package com.herafi.analytics;

import java.time.Instant;
import java.util.UUID;

import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.dto.JobStatus;
import com.herafi.analytics.service.ProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AnalyticsQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ProjectionService projections;

    @Test
    void scopesBothEndpointsToTheirAuthenticatedOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        projections.apply(EventFixtures.job(owner, jobId, JobEvent.Type.JOB_CREATED, JobStatus.OPEN));
        projections.apply(EventFixtures.application(applicant, UUID.randomUUID(), jobId, ApplicationEvent.Type.APPLICATION_SUBMITTED));

        mvc.perform(request("/jobs", owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records").doesNotExist())
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist());
        mvc.perform(request("/jobs", applicant)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(request("/applications", applicant)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(request("/applications", owner)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void usesTheSameJobFiltersForTotalsDistributionAndActivity() throws Exception {
        UUID owner = UUID.randomUUID();
        addJob(owner, JobStatus.OPEN, "PLUMBING", "CASABLANCA", EventFixtures.CREATED_AT);
        addJob(owner, JobStatus.CANCELLED, "PLUMBING", "CASABLANCA", EventFixtures.CREATED_AT);
        addJob(owner, JobStatus.OPEN, "PAINTING", "RABAT", EventFixtures.CREATED_AT);

        mvc.perform(request("/jobs", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.statusDistribution.length()").value(2))
                .andExpect(jsonPath("$.statusDistribution[0].count").value(1))
                .andExpect(jsonPath("$.statusDistribution[1].count").value(2))
                .andExpect(jsonPath("$.activity[0].count").value(3));
        mvc.perform(request("/jobs", owner).param("status", "OPEN").param("skill", "PLUMBING").param("city", "CASABLANCA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.statusDistribution.length()").value(1))
                .andExpect(jsonPath("$.statusDistribution[0].value").value("OPEN"))
                .andExpect(jsonPath("$.statusDistribution[0].count").value(1))
                .andExpect(jsonPath("$.activity[0].count").value(1));
    }

    @Test
    void usesTheSameApplicationFiltersForAllResponseSections() throws Exception {
        UUID owner = UUID.randomUUID();
        projections.apply(EventFixtures.application(owner, UUID.randomUUID(), UUID.randomUUID(), ApplicationEvent.Type.APPLICATION_SUBMITTED));
        projections.apply(EventFixtures.application(owner, UUID.randomUUID(), UUID.randomUUID(), ApplicationEvent.Type.APPLICATION_ACCEPTED));

        mvc.perform(request("/applications", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.statusDistribution.length()").value(2))
                .andExpect(jsonPath("$.activity[0].count").value(2));
        mvc.perform(request("/applications", owner).param("status", "ACCEPTED")
                        .param("skill", "PLUMBING").param("city", "CASABLANCA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.statusDistribution[0].value").value("ACCEPTED"))
                .andExpect(jsonPath("$.statusDistribution[0].count").value(1))
                .andExpect(jsonPath("$.activity[0].count").value(1));
        mvc.perform(request("/applications", owner).param("city", "RABAT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.statusDistribution").isEmpty())
                .andExpect(jsonPath("$.activity").isEmpty());
    }

    @Test
    void appliesInclusiveFromAndExclusiveToToBothResources() throws Exception {
        UUID owner = UUID.randomUUID();
        addJob(owner, JobStatus.OPEN, "PLUMBING", "CASABLANCA", EventFixtures.CREATED_AT);
        projections.apply(EventFixtures.application(owner, UUID.randomUUID(), UUID.randomUUID(), ApplicationEvent.Type.APPLICATION_SUBMITTED));

        for (String resource : new String[]{"/jobs", "/applications"}) {
            mvc.perform(request(resource, owner, EventFixtures.CREATED_AT.toString(), "2027-01-01T00:00:00Z"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
            mvc.perform(request(resource, owner, "2026-01-01T00:00:00Z", EventFixtures.CREATED_AT.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2026-02-20T23:30:00Z,day,2026-02-20T00:00",
            "2026-04-30T23:30:00Z,day,2026-05-01T00:00",
            "2026-04-30T23:30:00Z,week,2026-04-27T00:00",
            "2026-04-30T23:30:00Z,month,2026-05-01T00:00"
    })
    void bucketsInCasablancaIncludingItsSeasonalOffset(String createdAt, String granularity, String expected) throws Exception {
        UUID owner = UUID.randomUUID();
        addJob(owner, JobStatus.OPEN, "PLUMBING", "CASABLANCA", Instant.parse(createdAt));

        mvc.perform(request("/jobs", owner).param("granularity", granularity))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activity[0].value").value(expected));
    }

    @ParameterizedTest
    @CsvSource({"granularity,year", "status,ACCEPTED"})
    void rejectsInvalidJobFilters(String name, String value) throws Exception {
        mvc.perform(request("/jobs", UUID.randomUUID()).param(name, value)).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedDates() throws Exception {
        mvc.perform(request("/jobs", UUID.randomUUID(), "invalid", "2027-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(request("/jobs", UUID.randomUUID(), "2026-01-01T00:00:00Z", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"from,to", "to,from"})
    void requiresEachDateBoundary(String missing, String supplied) throws Exception {
        mvc.perform(get("/analytics/my/jobs")
                        .header("X-Gateway-Key", "test-gateway-key").header("X-User-Id", UUID.randomUUID())
                        .param(supplied, "2026-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(missing));
    }

    @Test
    void rejectsAnOmittedDateRange() throws Exception {
        mvc.perform(get("/analytics/my/jobs")
                        .header("X-Gateway-Key", "test-gateway-key").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void returnsFieldValidationErrorsAndRejectsReversedRanges() throws Exception {
        mvc.perform(request("/jobs", UUID.randomUUID()).param("skill", "X".repeat(101)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("skill"));
        mvc.perform(request("/jobs", UUID.randomUUID()).param("skill", "   "))
                .andExpect(status().isBadRequest());
        mvc.perform(request("/jobs", UUID.randomUUID(), "2026-06-01T00:00:00Z", "2026-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(request("/applications", UUID.randomUUID()).param("status", "OPEN"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/jobs", "/applications"})
    void requiresGatewayAuthentication(String resource) throws Exception {
        mvc.perform(get("/analytics/my" + resource).header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/analytics/my" + resource).header("X-Gateway-Key", "test-gateway-key").header("X-User-Id", "invalid"))
                .andExpect(status().isUnauthorized());
    }

    private UUID addJob(UUID owner, JobStatus status, String skill, String city, Instant createdAt) {
        UUID id = UUID.randomUUID();
        JobEvent.Type type = status == JobStatus.OPEN ? JobEvent.Type.JOB_CREATED : JobEvent.Type.JOB_STATUS_CHANGED;
        projections.apply(new JobEvent(UUID.randomUUID(), type, EventFixtures.OCCURRED_AT,
                new JobEvent.Data(id, owner, status, skill, city, createdAt)));
        return id;
    }

    private MockHttpServletRequestBuilder request(String resource, UUID owner) {
        return request(resource, owner, "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
    }

    private MockHttpServletRequestBuilder request(String resource, UUID owner, String from, String to) {
        return get("/analytics/my" + resource).header("X-Gateway-Key", "test-gateway-key").header("X-User-Id", owner)
                .param("from", from).param("to", to);
    }
}
