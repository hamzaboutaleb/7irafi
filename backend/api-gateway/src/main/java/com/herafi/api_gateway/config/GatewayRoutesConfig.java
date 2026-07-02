package com.herafi.api_gateway.config;

import static com.herafi.api_gateway.filter.GatewayIdentityFilter.addAuthenticatedIdentity;
import static com.herafi.api_gateway.filter.GatewayIdentityFilter.removeInternalHeaders;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    RouterFunction<ServerResponse> authServiceRoute(
            @Value("${services.auth-service.url}") String authServiceUrl) {
        return route("auth-service")
                .route(path("/auth/**"), http())
                .before(removeInternalHeaders())
                .before(uri(authServiceUrl))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> userServiceRoute(
            @Value("${services.user-service.url}") String userServiceUrl,
            @Value("${gateway.shared-secret}") String gatewaySharedSecret) {
        return route("user-service")
                .route(path("/users/**"), http())
                .before(addAuthenticatedIdentity(gatewaySharedSecret))
                .before(uri(userServiceUrl))
                .build();
    }
}
