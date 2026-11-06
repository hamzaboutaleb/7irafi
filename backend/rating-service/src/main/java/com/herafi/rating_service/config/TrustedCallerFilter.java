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

/**
 * Checks the caller's shared key before a request reaches a rating controller.
 *
 * The gateway checks the user's login token and supplies identity headers such
 * as X-User-Id. Without this filter, someone who could reach the rating service
 * directly could invent those headers. A matching gateway key shows that the
 * caller knows the shared secret; this filter does not check the user's token
 * or decide whether that user is allowed to rate a particular job.
 *
 * The @Component annotation lets Spring create this filter. Spring Boot registers it to run
 * before controllers, and OncePerRequestFilter manages its execution for each
 * normal incoming request.
 */
@Component
public class TrustedCallerFilter extends OncePerRequestFilter {

    private final String gatewaySharedSecret;
    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    /**
     * Spring reads both secrets from application configuration and supplies its
     * configured JSON writer. The gateway key is for gateway requests; the
     * internal API key is for other services calling internal endpoints here.
     * These keys stay on the server and should not be sent by the browser.
     */
    public TrustedCallerFilter(
            @Value("${service.security.gateway-shared-secret}") String gatewaySharedSecret,
            @Value("${service.security.internal-api-key}") String internalApiKey,
            ObjectMapper objectMapper) {
        this.gatewaySharedSecret = gatewaySharedSecret;
        this.internalApiKey = internalApiKey;
        this.objectMapper = objectMapper;
    }

    /**
     * Chooses the required key from the requested path. Missing or incorrect keys
     * stop the request with a JSON error. If the key is accepted, processing
     * continues to the next filter and eventually to the controller.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Rating endpoints trust identity headers written by the gateway, so a
        // caller must also provide the gateway's shared key.
        if (path.startsWith("/ratings")
                && !matches(request.getHeader("X-Gateway-Key"), gatewaySharedSecret)) {
            reject(response, "UNTRUSTED_GATEWAY", "Request did not come from the API gateway");
            // Writing an error alone does not stop processing; return prevents
            // this rejected request from continuing down the filter chain.
            return;
        }

        // This checks incoming calls to internal endpoints in this service.
        // JobClient and UserClient make outgoing calls; their receiving services
        // are responsible for checking the keys on those calls.
        if (path.startsWith("/internal/")
                && !matches(request.getHeader("X-Internal-Api-Key"), internalApiKey)) {
            reject(response, "UNAUTHORIZED_INTERNAL_REQUEST", "Invalid internal service key");
            return;
        }

        // Continue processing. Paths outside these two groups, such as the health
        // endpoint, also pass through without a key check from this filter.
        filterChain.doFilter(request, response);
    }

    /**
     * Checks the key from the request header against our configured secret.
     * A missing header is rejected.
     *
     * A comparison that stops at the first wrong character can give clues about
     * the secret: guesses with more correct characters may take longer to reject.
     * Someone making many requests could try to use those timing differences.
     *
     * MessageDigest.isEqual avoids stopping at the first different byte. This
     * reduces timing clues about matching characters; it does not guarantee that
     * every HTTP request takes the same time. Do not replace it with String.equals
     * or a loop that returns at the first mismatch.
     *
     * UTF-8 converts both keys to bytes using the same encoding. This comparison
     * does not hash or encrypt either key.
     */
    private boolean matches(String actual, String expected) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Writes a 401 response using the same error format as the rest of the API.
     * This filter runs before controller methods, so it writes the error directly
     * rather than relying on a controller exception handler to write it.
     *
     * UTF-8 supports text such as Arabic characters. The content type tells the
     * client to read the body as JSON. ApiErrorResponse defines the fields, and
     * ObjectMapper writes valid JSON, including escaping quotes and line breaks.
     * The caller must return after this method to stop the rejected request.
     */
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
