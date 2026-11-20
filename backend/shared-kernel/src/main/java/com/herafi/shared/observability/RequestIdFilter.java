package com.herafi.shared.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    private static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !VALID_REQUEST_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        long start = System.currentTimeMillis();
        boolean failed = false;
        try {
            filterChain.doFilter(request, new SingleRequestIdResponse(response));
        } catch (Throwable exception) {
            failed = true;
            throw exception;
        } finally {
            if (!request.getRequestURI().startsWith("/actuator")) {
                long durationMs = System.currentTimeMillis() - start;
                int status = loggedStatus(response, failed);
                log.atInfo()
                        .addKeyValue("http.method", request.getMethod())
                        .addKeyValue("http.path", request.getRequestURI())
                        .addKeyValue("http.status", status)
                        .addKeyValue("duration_ms", durationMs)
                        .log("{} {} {} {}ms",
                                request.getMethod(),
                                request.getRequestURI(),
                                status,
                                durationMs);
            }
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * An exception that escapes the chain becomes a 500 only after this filter
     * returns, so the response would still report 200 here. If the response was
     * already sent, its status is what the client got.
     */
    private static int loggedStatus(HttpServletResponse response, boolean failed) {
        return failed && !response.isCommitted()
                ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                : response.getStatus();
    }

    /**
     * Keeps the id set above as the only X-Request-Id. Downstream services echo
     * the id they receive, and the proxy adds their response headers next to
     * ours, so a forwarded response would otherwise carry it twice.
     */
    private static final class SingleRequestIdResponse extends HttpServletResponseWrapper {

        SingleRequestIdResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!HEADER.equalsIgnoreCase(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!HEADER.equalsIgnoreCase(name)) {
                super.addHeader(name, value);
            }
        }
    }
}
