package com.herafi.application_service.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GatewayKeyFilter extends OncePerRequestFilter {

    private static final String GATEWAY_KEY_HEADER = "X-Gateway-Key";

    private final String gatewaySharedSecret;
    private final ApiErrorWriter apiErrorWriter;

    public GatewayKeyFilter(
            @Value("${gateway.shared-secret}") String gatewaySharedSecret,
            ApiErrorWriter apiErrorWriter) {
        this.gatewaySharedSecret = gatewaySharedSecret;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return !path.equals("/applications") && !path.startsWith("/applications/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!matches(request.getHeader(GATEWAY_KEY_HEADER), gatewaySharedSecret)) {
            apiErrorWriter.writeUnauthorized(
                    request,
                    response,
                    "Request did not come from the API gateway");
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

}
