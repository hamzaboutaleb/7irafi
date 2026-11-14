package com.herafi.application_service.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.herafi.application_service.event.NotificationRequestedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private static final String TOPIC = "notification.request";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public void publish(NotificationRequestedEvent event) {
        try {
            String payload = mapper.writeValueAsString(event);
            kafka.send(TOPIC, event.recipientId().toString(), payload)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            logFailure(event, failure);
                        }
                    });
        } catch (RuntimeException failure) {
            logFailure(event, failure);
        }
    }

    private void logFailure(NotificationRequestedEvent event, Throwable failure) {
        log.error("Notification publish failed: eventId={}, recipientId={}, type={}",
                event.eventId(), event.recipientId(), event.type(), failure);
    }
}
