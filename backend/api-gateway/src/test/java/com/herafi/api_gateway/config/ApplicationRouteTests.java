package com.herafi.api_gateway.config;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRouteTests {
    @Test
    void routesApplicationRootAndNestedPathsOnly() {
        var route = new GatewayRoutesConfig().applicationServiceRoute("test-key");
        for (String path : List.of("/applications", "/applications/my", "/applications/123/accept")) {
            var request = ServerRequest.create(new MockHttpServletRequest("PATCH", path), List.of());
            assertThat(route.route(request)).isPresent();
        }
        var jobRequest = ServerRequest.create(new MockHttpServletRequest("GET", "/jobs"), List.of());
        assertThat(route.route(jobRequest)).isEmpty();
    }

    @Test
    void permitsPatchPreflightFromFrontend() {
        var request = new MockHttpServletRequest("OPTIONS", "/applications/123/accept");
        var cors = new SecurityConfig().corsConfigurationSource("http://localhost:4200")
                .getCorsConfiguration(request);
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedMethods()).contains("PATCH");
        assertThat(cors.checkOrigin("http://localhost:4200")).isEqualTo("http://localhost:4200");
        assertThat(cors.checkOrigin("http://untrusted.example")).isNull();
    }
}
