package com.herafi.analytics.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
    @Validated
    @ConfigurationProperties("spring.datasource")
    public record Database(@NotBlank String url, @NotBlank String username, @NotBlank String password) { }

    @Validated
    @ConfigurationProperties("spring.kafka")
    public record Kafka(@NotEmpty List<@NotBlank String> bootstrapServers) { }

    @Validated
    @ConfigurationProperties("eureka.client.service-url")
    public record Discovery(@NotBlank String defaultZone) { }

    @Validated
    @ConfigurationProperties("gateway")
    public record Gateway(@NotBlank String sharedSecret) { }

    @Validated
    @ConfigurationProperties("security.jwt")
    public record Jwt(@NotBlank String secret, @NotBlank String issuer) { }

    @Validated
    @ConfigurationProperties("analytics")
    public record Socket(@NotBlank String allowedOrigin) { }
}
