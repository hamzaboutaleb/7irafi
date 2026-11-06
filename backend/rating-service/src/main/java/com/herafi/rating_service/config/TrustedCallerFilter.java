package com.herafi.rating_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.herafi.rating_service.dto.ApiErrorResponse;

import tools.jackson.databind.ObjectMapper;

@Component
public class TrustedCallerFilter extends OncePerRequestFilter {

    private final String gatewaySharedSecret;
    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    public TrustedCallerFilter(
            @Value("${service.security.gateway-shared-secret}") String gatewaySharedSecret,
            @Value("${service.security.internal-api-key}") String internalApiKey,
            ObjectMapper objectMapper) {
        this.gatewaySharedSecret = gatewaySharedSecret;
        this.internalApiKey = internalApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/ratings")
                && !matches(request.getHeader("X-Gateway-Key"), gatewaySharedSecret)) {
            reject(response, "UNTRUSTED_GATEWAY", "Request did not come from the API gateway");
            return;
        }

        if (path.startsWith("/internal/")
                && !matches(request.getHeader("X-Internal-Api-Key"), internalApiKey)) {
            reject(response, "UNAUTHORIZED_INTERNAL_REQUEST", "Invalid internal service key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String actual, String expected) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void reject(
            HttpServletResponse response,
            String code,
            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorResponse.of(code, message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
