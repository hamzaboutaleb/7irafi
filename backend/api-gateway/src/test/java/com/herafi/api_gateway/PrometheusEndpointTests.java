package com.herafi.api_gateway;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// src/test/resources/application.properties replaces the main file in tests,
// so the endpoint exposure is set here.
@SpringBootTest(properties = {
        "security.jwt.secret=7irafi-test-jwt-secret-that-is-at-least-32-bytes",
        "security.jwt.issuer=7irafi-auth-service-test",
        "gateway.shared-secret=test-gateway-key",
        "gateway.cors.allowed-origin=http://localhost:4200",
        "metrics.username=prometheus",
        "metrics.password=test-metrics-password",
        "management.endpoints.web.exposure.include=health,prometheus",
})
@AutoConfigureMockMvc
class PrometheusEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusAllowsTheConfiguredCredentials() throws Exception {
        String basic = Base64.getEncoder()
                .encodeToString("prometheus:test-metrics-password".getBytes());

        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Basic " + basic))
                .andExpect(status().isOk());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void businessRoutesKeepTheBearerChallenge() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }
}
