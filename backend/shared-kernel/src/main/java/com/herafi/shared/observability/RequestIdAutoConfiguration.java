package com.herafi.shared.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Services only scan their own package, so the request-id beans are registered
 * here. Adding the shared-kernel dependency is enough to get both.
 */
@AutoConfiguration
public class RequestIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    RequestIdInterceptor requestIdInterceptor() {
        return new RequestIdInterceptor();
    }
}
