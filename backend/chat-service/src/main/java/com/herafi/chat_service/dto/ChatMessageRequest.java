package com.herafi.chat_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a client publishes to /app/chat.
 *
 * No recipient: it is derived from the conversation, so a sender cannot pick who
 * receives their message.
 */
public record ChatMessageRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 4000) String text) {
}
