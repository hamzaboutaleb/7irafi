package com.herafi.notification_service.config;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class TrustedCallerFilter extends OncePerRequestFilter {
    private final String gatewaySharedSecret;

    public TrustedCallerFilter(
            @Value("${service.security.gateway-shared-secret}") String gatewaySharedSecret) {
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/notifications") && !matches(request.getHeader("X-Gateway-Key"), gatewaySharedSecret)) {
            reject(response, "UNTRUSTED_GATEWAY", "Request did not come from the API gateway");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String actual, String expected) {
        if (actual == null) {
            return false;
        }

        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(
            HttpServletResponse response,
            String code,
            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"success":false,"error":{"code":"%s","message":"%s","errors":[]}}
                """.formatted(code, message).trim());
    }
}
