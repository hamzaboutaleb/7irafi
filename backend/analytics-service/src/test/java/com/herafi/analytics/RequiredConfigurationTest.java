package com.herafi.analytics;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import com.herafi.analytics.config.RequiredConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredConfigurationTest {
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
            .withUserConfiguration(RequiredConfiguration.class)
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

    static Stream<String> requiredSettings() {
        return SETTINGS.keySet().stream();
    }

    @Test
    void acceptsExplicitDeploymentConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @MethodSource("requiredSettings")
    void failsStartupWhenARequiredVariableIsMissing(String name) {
        runner.withInitializer(context -> {
            var settings = new java.util.HashMap<>(SETTINGS);
            settings.remove(name);
            context.getEnvironment().getPropertySources()
                    .replace("deployment", new MapPropertySource("deployment", settings));
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(name);
        });
    }

    @ParameterizedTest
    @MethodSource("requiredSettings")
    void failsStartupWhenARequiredVariableIsBlank(String name) {
        runner.withInitializer(context -> {
            var settings = new java.util.HashMap<>(SETTINGS);
            settings.put(name, "   ");
            context.getEnvironment().getPropertySources()
                    .replace("deployment", new MapPropertySource("deployment", settings));
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("must not be blank");
        });
    }
}
