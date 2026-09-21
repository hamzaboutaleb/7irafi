package com.herafi.chat_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.chat_service.entity.Message;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String text,
        Instant sentAt) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getBody(),
                message.getCreatedAt());
    }
}
