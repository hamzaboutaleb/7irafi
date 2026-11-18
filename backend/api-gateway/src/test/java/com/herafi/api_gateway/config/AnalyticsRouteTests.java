package com.herafi.api_gateway.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsRouteTests {
    @Test void routesOnlyAnalyticsPaths() {
        var route = new GatewayRoutesConfig().analyticsServiceRoute("test-key");
        var analytics = ServerRequest.create(new MockHttpServletRequest("GET", "/analytics/my/jobs"), List.of());
        var other = ServerRequest.create(new MockHttpServletRequest("GET", "/jobs/my"), List.of());
        assertThat(route.route(analytics)).isPresent();
        assertThat(route.route(other)).isEmpty();
    }
}
