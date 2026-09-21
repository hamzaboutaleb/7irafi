package com.herafi.chat_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.chat_service.entity.Conversation;

public record ConversationResponse(
        UUID id,
        UUID applicationId,
        UUID jobId,
        UUID clientId,
        UUID workerId,
        Instant createdAt) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getApplicationId(),
                conversation.getJobId(),
                conversation.getClientId(),
                conversation.getWorkerId(),
                conversation.getCreatedAt());
    }
}
