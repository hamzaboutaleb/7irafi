package com.herafi.chat_service.dto;

public record ChatMessageRequest(String conversationId, String recipientId, String text) {

}
