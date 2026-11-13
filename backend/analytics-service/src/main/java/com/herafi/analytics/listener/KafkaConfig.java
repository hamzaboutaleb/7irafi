package com.herafi.analytics.listener;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Malformed contracts must block for repair too; never recover by skipping a record.
        handler.setClassifications(Map.of(), true);
        return handler;
    }
}
