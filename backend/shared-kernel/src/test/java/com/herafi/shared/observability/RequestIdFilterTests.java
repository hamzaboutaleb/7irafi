package com.herafi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

    @Test
    void keepsASingleIdWhenTheProxiedResponseEchoesOne() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // The proxy copies the downstream response headers with addHeader.
        filter.doFilter(request, response, (req, res) ->
                ((HttpServletResponse) res).addHeader("x-request-id", "downstream-copy"));

        assertThat(response.getHeaders("X-Request-Id")).containsExactly("request-123");
    }

    @Test
    void logsA500WhenTheChainThrows() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RequestIdFilter filter = new RequestIdFilter();
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getFormattedMessage()).startsWith("POST /auth/login 500 ");
            assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                assertThat(pair.key).isEqualTo("http.status");
                assertThat(pair.value).isEqualTo(500);
            });
            assertThat(MDC.get("requestId")).isNull();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void setsRequestIdInTheMdcAndClearsItAfterwards() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("request-123");
        });

        assertThat(MDC.get("requestId")).isNull();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123");
    }
}
