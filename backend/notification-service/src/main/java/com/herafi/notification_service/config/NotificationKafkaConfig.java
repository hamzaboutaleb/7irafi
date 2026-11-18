package com.herafi.notification_service.config;

import jakarta.validation.ConstraintViolationException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Slf4j
@Configuration
public class NotificationKafkaConfig {

    @Bean
    DefaultErrorHandler notificationErrorHandler() {
        var handler = new DefaultErrorHandler((record, exception) -> log.error(
                "Skipping invalid notification request: topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception),
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));

        handler.addNotRetryableExceptions(JacksonException.class, ConstraintViolationException.class);
        return handler;
    }
}
