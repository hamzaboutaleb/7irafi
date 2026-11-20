package com.herafi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RequestIdAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestIdAutoConfiguration.class));

    @Test
    void registersTheFilterAndTheInterceptor() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RequestIdFilter.class);
            assertThat(context).hasSingleBean(RequestIdInterceptor.class);
        });
    }

    @Test
    void backsOffWhenTheServiceDefinesItsOwn() {
        runner.withUserConfiguration(OwnBeans.class).run(context -> {
            assertThat(context).hasSingleBean(RequestIdFilter.class);
            assertThat(context).hasSingleBean(RequestIdInterceptor.class);
            assertThat(context.getBean(RequestIdFilter.class)).isSameAs(OwnBeans.FILTER);
            assertThat(context.getBean(RequestIdInterceptor.class)).isSameAs(OwnBeans.INTERCEPTOR);
        });
    }

    @Configuration
    static class OwnBeans {
        static final RequestIdFilter FILTER = new RequestIdFilter();
        static final RequestIdInterceptor INTERCEPTOR = new RequestIdInterceptor();

        @Bean
        RequestIdFilter ownFilter() {
            return FILTER;
        }

        @Bean
        RequestIdInterceptor ownInterceptor() {
            return INTERCEPTOR;
        }
    }
}
