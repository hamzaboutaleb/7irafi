package com.herafi.analytics.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] sharedSecret;

    public GatewayAuthenticationFilter(String sharedSecret) {
        this.sharedSecret = sharedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/analytics/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!matchesSecret(request.getHeader("X-Gateway-Key"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!isUserId(request.getHeader("X-User-Id"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean matchesSecret(String key) {
        return key != null && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), sharedSecret);
    }

    private boolean isUserId(String userId) {
        if (userId == null) {
            return false;
        }
        try {
            UUID.fromString(userId);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
