package com.herafi.chat_service.dto;

import java.time.Instant;

public record ChatMessageResponse(String conversationId, String senderId, String text, Instant sentAt) {

}
