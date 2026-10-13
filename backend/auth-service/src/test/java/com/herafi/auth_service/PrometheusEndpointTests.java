package com.herafi.auth_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// src/test/resources/application.properties replaces the main file in tests,
// so the endpoint exposure and the application tag are set here.
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.metrics.tags.application=auth-service",
})
@AutoConfigureMockMvc
class PrometheusEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesPrometheusMetricsWithoutAuthentication() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("application=\"auth-service\"");
    }
}
