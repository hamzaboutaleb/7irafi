package com.herafi.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

class GatewayIdentityFilterTests {

    private static final String GATEWAY_SECRET = "test-gateway-secret";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void removesTrustedHeadersFromPublicAuthRequests() {
        ServerRequest request = requestWithForgedHeaders();

        ServerRequest filtered = GatewayIdentityFilter.removeInternalHeaders().apply(request);

        assertThat(filtered.headers().firstHeader("X-User-Id")).isNull();
        assertThat(filtered.headers().firstHeader("X-User-Email")).isNull();
        assertThat(filtered.headers().firstHeader("X-User-Role")).isNull();
        assertThat(filtered.headers().firstHeader("X-Gateway-Key")).isNull();
        assertThat(filtered.headers().firstHeader("X-Internal-Api-Key")).isNull();
        assertThat(filtered.headers().firstHeader("X-Request-Id")).isEqualTo("request-123");
    }

    @Test
    void replacesForgedHeadersWithAuthenticatedJwtClaims() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4")
                .claim("email", "real@example.com")
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));

        ServerRequest filtered = GatewayIdentityFilter
                .addAuthenticatedIdentity(GATEWAY_SECRET)
                .apply(requestWithForgedHeaders());

        assertThat(filtered.headers().firstHeader("X-User-Id"))
                .isEqualTo("9d5036ab-2cd6-4eb6-aa54-bc29e6d03aa4");
        assertThat(filtered.headers().firstHeader("X-User-Email"))
                .isEqualTo("real@example.com");
        assertThat(filtered.headers().firstHeader("X-User-Role")).isEqualTo("USER");
        assertThat(filtered.headers().firstHeader("X-Gateway-Key"))
                .isEqualTo(GATEWAY_SECRET);
        assertThat(filtered.headers().firstHeader("X-Internal-Api-Key")).isNull();
    }

    @Test
    void rejectsIdentityInjectionWithoutAuthenticatedJwt() {
        assertThatThrownBy(() -> GatewayIdentityFilter
                .addAuthenticatedIdentity(GATEWAY_SECRET)
                .apply(requestWithForgedHeaders()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated JWT is missing");
    }

    private ServerRequest requestWithForgedHeaders() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/users/me");
        servletRequest.addHeader("X-User-Id", "forged-id");
        servletRequest.addHeader("X-User-Email", "attacker@example.com");
        servletRequest.addHeader("X-User-Role", "ADMIN");
        servletRequest.addHeader("X-Gateway-Key", "forged-gateway-key");
        servletRequest.addHeader("X-Internal-Api-Key", "forged-internal-key");
        servletRequest.addHeader("X-Request-Id", "request-123");
        return ServerRequest.create(servletRequest, List.of());
    }
}
