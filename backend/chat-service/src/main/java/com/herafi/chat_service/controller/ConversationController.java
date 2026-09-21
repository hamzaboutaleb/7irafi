package com.herafi.chat_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.chat_service.dto.ConversationResponse;
import com.herafi.chat_service.dto.MessageResponse;
import com.herafi.chat_service.dto.OpenConversationRequest;
import com.herafi.chat_service.dto.PageResponse;
import com.herafi.chat_service.service.ConversationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The HTTP half of chat: opening conversations and reading history. Live
 * messages travel over the websocket instead.
 *
 * Identity always comes from X-User-Id, which only the gateway can set.
 */
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse open(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody OpenConversationRequest request) {
        return ConversationResponse.from(conversationService.open(request.applicationId(), userId));
    }

    @GetMapping
    public List<ConversationResponse> mine(@RequestHeader("X-User-Id") UUID userId) {
        return conversationService.listForUser(userId).stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse one(
            @PathVariable UUID conversationId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ConversationResponse.from(conversationService.getForParticipant(conversationId, userId));
    }

    @GetMapping("/{conversationId}/messages")
    public PageResponse<MessageResponse> messages(
            @PathVariable UUID conversationId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return PageResponse.from(
                conversationService.messages(conversationId, userId, page, size),
                MessageResponse::from);
    }
}
