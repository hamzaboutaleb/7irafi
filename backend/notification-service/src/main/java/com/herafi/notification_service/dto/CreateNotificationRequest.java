package com.herafi.notification_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What another service sends when something happened. The producing service
 * decides the wording, because only it knows the context.
 */
public record CreateNotificationRequest(
        @NotNull UUID recipientId,
        @NotBlank @Size(max = 60) String type,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 500) String body,
        @Size(max = 300) String targetUrl) {
}
