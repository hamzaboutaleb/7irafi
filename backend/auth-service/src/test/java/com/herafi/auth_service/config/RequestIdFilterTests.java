package com.herafi.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {

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

    @Test
    void generatesAnIdWhenTheRequestHasNone() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isNotBlank();
        });

        assertThat(response.getHeader("X-Request-Id")).matches("[A-Za-z0-9-]{1,64}");
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
}
