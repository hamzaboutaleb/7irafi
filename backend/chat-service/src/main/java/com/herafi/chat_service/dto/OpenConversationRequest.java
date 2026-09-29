package com.herafi.chat_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record OpenConversationRequest(@NotNull UUID applicationId) {
}
