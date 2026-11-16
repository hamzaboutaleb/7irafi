package com.herafi.analytics;

import java.io.IOException;
import java.util.Map;

import com.herafi.analytics.config.AnalyticsProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsPropertiesTest {
    private static final Map<String, Object> SETTINGS = Map.of(
            "DB_URL", "jdbc:postgresql://localhost:55439/analytics_test",
            "DB_USERNAME", "test-user",
            "DB_PASSWORD", "test-password",
            "KAFKA_BOOTSTRAP_SERVERS", "localhost:9094",
            "GATEWAY_SHARED_SECRET", "test-gateway-key",
            "JWT_SECRET", "test-jwt-secret-with-at-least-32-bytes",
            "JWT_ISSUER", "test-issuer",
            "FRONTEND_ORIGIN", "http://localhost:4200",
            "DISCOVERY_SERVICE_URL", "http://localhost:8761/eureka/");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AnalyticsProperties.class)
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                // Isolate deployment configuration from the developer's shell environment.
                sources.remove("systemEnvironment");
                sources.remove("systemProperties");
                sources.addFirst(new MapPropertySource("deployment", SETTINGS));
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                            .forEach(sources::addLast);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });

    @Test
    void acceptsExplicitDeploymentConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsStartupWhenTheJwtSecretIsMissing() {
        runner.withInitializer(context -> {
            var settings = new java.util.HashMap<>(SETTINGS);
            settings.remove("JWT_SECRET");
            context.getEnvironment().getPropertySources()
                    .replace("deployment", new MapPropertySource("deployment", settings));
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @Test
    void failsStartupWhenTheGatewaySecretIsBlank() {
        runner.withInitializer(context -> {
            var settings = new java.util.HashMap<>(SETTINGS);
            settings.put("GATEWAY_SHARED_SECRET", "   ");
            context.getEnvironment().getPropertySources()
                    .replace("deployment", new MapPropertySource("deployment", settings));
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }
}
