package com.herafi.job_service.config;

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
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final String internalApiKey;
    private final ApiErrorWriter apiErrorWriter;

    public InternalApiKeyFilter(
            @Value("${service.security.internal-api-key}") String internalApiKey,
            ApiErrorWriter apiErrorWriter) {
        this.internalApiKey = internalApiKey;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return !path.equals("/internal") && !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!matches(request.getHeader(INTERNAL_API_KEY_HEADER), internalApiKey)) {
            apiErrorWriter.writeUnauthorized(
                    request,
                    response,
                    "Invalid internal service key");
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
