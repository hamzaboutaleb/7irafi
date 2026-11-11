package com.herafi.analytics.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/** Reject empty deployment settings as well as unresolved environment variables. */
@Configuration(proxyBeanMethods = false)
public class RequiredConfiguration {
    public RequiredConfiguration(Environment environment) {
        for (String property : List.of(
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "spring.kafka.bootstrap-servers",
                "gateway.shared-secret",
                "security.jwt.secret",
                "security.jwt.issuer",
                "analytics.allowed-origin",
                "eureka.client.serviceUrl.defaultZone")) {
            Assert.hasText(environment.getRequiredProperty(property), property + " must not be blank");
        }
    }
}
