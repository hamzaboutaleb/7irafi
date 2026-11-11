package com.herafi.notification_service.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class NotificationKafkaConfig {

    @Bean
    DefaultErrorHandler notificationErrorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Keep failed requests pending, including invalid messages, until resolved.
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
