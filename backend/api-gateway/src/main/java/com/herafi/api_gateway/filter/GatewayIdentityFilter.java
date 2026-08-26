package com.herafi.api_gateway.filter;

import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

public final class GatewayIdentityFilter {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_EMAIL = "X-User-Email";
    private static final String USER_ROLE = "X-User-Role";
    private static final String GATEWAY_KEY = "X-Gateway-Key";
    private static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

    private GatewayIdentityFilter() {
    }

    public static Function<ServerRequest, ServerRequest> removeInternalHeaders() {
        return request -> ServerRequest.from(request)
                .headers(GatewayIdentityFilter::removeTrustedHeaders)
                .build();
    }

    public static Function<ServerRequest, ServerRequest> addAuthenticatedIdentity(
            String gatewaySharedSecret) {
        return request -> {
            Authentication authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
                throw new IllegalStateException("Authenticated JWT is missing");
            }

            String email = jwtAuthentication.getToken().getClaimAsString("email");
            String role = jwtAuthentication.getToken().getClaimAsString("role");

            return ServerRequest.from(request)
                    .headers(headers -> {
                        removeTrustedHeaders(headers);
                        headers.set(USER_ID, jwtAuthentication.getToken().getSubject());
                        headers.set(USER_EMAIL, email);
                        headers.set(USER_ROLE, role);
                        headers.set(GATEWAY_KEY, gatewaySharedSecret);
                    })
                    .build();
        };
    }

    private static void removeTrustedHeaders(HttpHeaders headers) {
        headers.remove(USER_ID);
        headers.remove(USER_EMAIL);
        headers.remove(USER_ROLE);
        headers.remove(GATEWAY_KEY);
        headers.remove(INTERNAL_API_KEY);
    }
}
