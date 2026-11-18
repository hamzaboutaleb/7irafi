package com.herafi.analytics.listener;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Malformed contracts must block for repair too; never recover by skipping a record.
        handler.setClassifications(Map.of(), true);
        handler.setRetryListeners((record, exception, attempt) -> log.error(
                "Analytics event failed; retrying topic={} partition={} offset={} attempt={}",
                record.topic(), record.partition(), record.offset(), attempt, exception));
        return handler;
    }
}
