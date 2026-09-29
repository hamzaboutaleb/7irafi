package com.herafi.chat_service.controller;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.herafi.chat_service.client.NotificationClient;
import com.herafi.chat_service.dto.ChatMessageRequest;
import com.herafi.chat_service.dto.ChatMessageResponse;
import com.herafi.chat_service.service.ConversationService;

import jakarta.validation.Valid;

/**
 * Live messages. Clients publish to /app/chat and receive on
 * /user/queue/messages.
 */
@Controller
public class ChatController {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ConversationService conversationService;
    private final NotificationClient notificationClient;

    public ChatController(
            SimpMessagingTemplate simpMessagingTemplate,
            ConversationService conversationService,
            NotificationClient notificationClient) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.conversationService = conversationService;
        this.notificationClient = notificationClient;
    }

    @MessageMapping("/chat")
    public void sendMessage(@Valid ChatMessageRequest messageRequest, Principal principal) {
        // The sender comes from the authenticated session, never from the payload.
        UUID senderId = UUID.fromString(principal.getName());

        ConversationService.Delivery delivery = conversationService.send(
                messageRequest.conversationId(), senderId, messageRequest.text());

        ChatMessageResponse message = ChatMessageResponse.from(delivery.message());

        simpMessagingTemplate.convertAndSendToUser(
                delivery.recipientId().toString(), "/queue/messages", message);

        // Echoed back so the sender's own UI gets the stored id and timestamp
        // instead of inventing them, and sees the message on its other devices.
        simpMessagingTemplate.convertAndSendToUser(
                senderId.toString(), "/queue/messages", message);

        // So the recipient still learns about it while they are on another page
        // or offline entirely.
        notificationClient.notify(
                delivery.recipientId(),
                "NEW_MESSAGE",
                "New message",
                messageRequest.text(),
                "/conversations/" + messageRequest.conversationId());
    }
}
