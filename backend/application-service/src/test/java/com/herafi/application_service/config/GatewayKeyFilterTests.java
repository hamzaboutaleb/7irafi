package com.herafi.application_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayKeyFilterTests {
    private final GatewayKeyFilter filter = new GatewayKeyFilter(
            "test-key", new ApiErrorWriter(new ObjectMapper()));

    @Test
    void rejectsMissingAndInvalidGatewayKeys() throws Exception {
        for (String path : new String[]{"/applications", "/applications/my"}) {
            for (String key : new String[]{null, "wrong-key"}) {
                var request = new MockHttpServletRequest("GET", path);
                if (key != null) request.addHeader("X-Gateway-Key", key);
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (req, res) -> {
                    throw new AssertionError("Untrusted request reached the application");
                });
                assertThat(response.getStatus()).isEqualTo(401);
            }
        }
    }

    @Test
    void allowsTrustedGatewayRequests() throws Exception {
        var request = new MockHttpServletRequest("GET", "/applications/my");
        request.addHeader("X-Gateway-Key", "test-key");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> response.setStatus(204));
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void healthDoesNotRequireGatewayKey() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), response,
                (req, res) -> response.setStatus(204));
        assertThat(response.getStatus()).isEqualTo(204);
    }
}
