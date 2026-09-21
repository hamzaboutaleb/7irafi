package com.herafi.chat_service.controller;

import java.security.Principal;
import java.time.Instant;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.herafi.chat_service.dto.ChatMessageRequest;
import com.herafi.chat_service.dto.ChatMessageResponse;

@Controller 
public class ChatController {
    private final SimpMessagingTemplate simpMessagingTemplate;

    public ChatController(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @MessageMapping("/chat")
    public void sendMessage(ChatMessageRequest messageRequest, Principal principal) {
        // The sender is taken from the authenticated session, never from the
        // payload: otherwise any client could send messages as someone else.
        ChatMessageResponse message = new ChatMessageResponse(
                messageRequest.conversationId(),
                principal.getName(),
                messageRequest.text(),
                Instant.now());

        this.simpMessagingTemplate.convertAndSendToUser(
                messageRequest.recipientId(), "/queue/messages", message);
    }
}
