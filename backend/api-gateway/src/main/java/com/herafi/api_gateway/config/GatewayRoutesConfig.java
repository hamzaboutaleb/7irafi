package com.herafi.api_gateway.config;

import static com.herafi.api_gateway.filter.GatewayIdentityFilter.addAuthenticatedIdentity;
import static com.herafi.api_gateway.filter.GatewayIdentityFilter.removeInternalHeaders;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;

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
}
