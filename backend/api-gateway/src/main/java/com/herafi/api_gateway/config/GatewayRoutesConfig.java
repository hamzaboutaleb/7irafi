package com.herafi.api_gateway.config;

import static com.herafi.api_gateway.filter.GatewayIdentityFilter.addAuthenticatedIdentity;
import static com.herafi.api_gateway.filter.GatewayIdentityFilter.removeInternalHeaders;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> authServiceRoute() {
        return route("auth-service")
                .route(path("/auth/**"), http())
                .before(removeInternalHeaders())
                // 5 login/register attempts per minute per client IP, then 429.
                .filter(rateLimit(config -> config
                        .setCapacity(5)
                        .setPeriod(Duration.ofMinutes(1))
                        .setKeyResolver(request -> request.servletRequest().getRemoteAddr())))
                .filter(lb("AUTH-SERVICE"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> userServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("user-service")
                .route(path("/users/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("USER-SERVICE"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> jobServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("job-service")
                .route(path("/jobs/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("JOB-SERVICE"))
                .build();
    }
    @Bean
    RouterFunction<ServerResponse> applicationServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("application-service")
                .route(path("/applications/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("APPLICATION-SERVICE"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> analyticsServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("analytics-service")
                .route(path("/analytics/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("ANALYTICS-SERVICE"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> chatServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("chat-service")
                .route(path("/conversations/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("CHAT-SERVICE"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> notificationServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("notification-service")
                .route(path("/notifications/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("NOTIFICATION-SERVICE"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> ratingServiceRoute(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("rating-service")
                .route(path("/ratings/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .filter(lb("RATING-SERVICE"))
                .build();
    }
}
