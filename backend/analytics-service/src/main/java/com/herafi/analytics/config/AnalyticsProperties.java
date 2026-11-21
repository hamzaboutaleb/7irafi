package com.herafi.analytics.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AnalyticsProperties.Database.class,
        AnalyticsProperties.Kafka.class,
        AnalyticsProperties.Discovery.class,
        AnalyticsProperties.Gateway.class,
        AnalyticsProperties.Jwt.class,
        AnalyticsProperties.Socket.class
})
public class AnalyticsProperties {
    // Spring's configuration binder can retain unresolved placeholders as strings.
    private static final String CONFIGURED_VALUE = "(?!\\$\\{[^}]+\\}$)[\\s\\S]*";

    @Validated
    @ConfigurationProperties("spring.datasource")
    public record Database(
            @NotBlank @Pattern(regexp = CONFIGURED_VALUE) String url,
            @NotBlank @Pattern(regexp = CONFIGURED_VALUE) String username,
            @NotBlank @Pattern(regexp = CONFIGURED_VALUE) String password) { }

    @Validated
    @ConfigurationProperties("spring.kafka")
    public record Kafka(@NotEmpty List<@NotBlank @Pattern(regexp = CONFIGURED_VALUE) String> bootstrapServers) { }

    @Validated
    @ConfigurationProperties("eureka.client.service-url")
    public record Discovery(@NotBlank @Pattern(regexp = CONFIGURED_VALUE) String defaultZone) { }

    @Validated
    @ConfigurationProperties("gateway")
    public record Gateway(@NotBlank @Pattern(regexp = CONFIGURED_VALUE) String sharedSecret) { }

    @Validated
    @ConfigurationProperties("security.jwt")
    public record Jwt(
            @NotBlank @Pattern(regexp = CONFIGURED_VALUE) String secret,
            @NotBlank @Pattern(regexp = CONFIGURED_VALUE) String issuer) { }

    @Validated
    @ConfigurationProperties("analytics")
    public record Socket(@NotBlank @Pattern(regexp = CONFIGURED_VALUE) String allowedOrigin) { }
}
