package com.herafi.chat_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.chat_service.entity.Message;

/** What both participants receive on /user/queue/messages. */
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String text,
        Instant sentAt) {

    public static ChatMessageResponse from(Message message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getBody(),
                message.getCreatedAt());
    }
}
