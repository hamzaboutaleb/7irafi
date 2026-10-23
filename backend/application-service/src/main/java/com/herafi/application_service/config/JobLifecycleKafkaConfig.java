package com.herafi.application_service.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class JobLifecycleKafkaConfig {

    @Bean
    DefaultErrorHandler jobLifecycleErrorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Never advance past a failed database transaction or discard an invalid lifecycle message.
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
