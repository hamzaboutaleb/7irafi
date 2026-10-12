package com.herafi.api_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String id = response.getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
        assertThat(id).matches("[A-Za-z0-9-]{1,64}");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void keepsAValidIncomingId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesAnInvalidIncomingId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.addHeader("X-Request-Id", "bad id!!");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String id = response.getHeader("X-Request-Id");
        assertThat(id).isNotEqualTo("bad id!!");
        assertThat(id).matches("[A-Za-z0-9-]{1,64}");
        assertThat(MDC.get("requestId")).isNull();
    }
}
