package com.herafi.notification_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.notification_service.entity.Notification;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String targetUrl,
        Instant createdAt,
        boolean read) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetUrl(),
                notification.getCreatedAt(),
                notification.getReadAt() != null);
    }
}
