package com.herafi.notification_service.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.herafi.notification_service.service.NotificationService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NotificationRequestedListener {

    private final ObjectMapper mapper;
    private final NotificationService notificationService;

    @KafkaListener(id = "notification-request", topics = "notification.request",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        notificationService.createFromEvent(mapper.readValue(payload, NotificationRequestedEvent.class));
    }
}
