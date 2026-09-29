package com.herafi.chat_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.chat_service.client.ApplicationClient;
import com.herafi.chat_service.entity.Conversation;
import com.herafi.chat_service.entity.Message;
import com.herafi.chat_service.exception.ChatException;
import com.herafi.chat_service.repository.ConversationRepository;
import com.herafi.chat_service.repository.MessageRepository;

/**
 * Who may talk to whom, and the messages they exchange.
 *
 * Permission is resolved once, when the conversation is opened, by asking
 * application-service. From then on the two participants are read from chat_db.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ApplicationClient applicationClient;

    public ConversationService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ApplicationClient applicationClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.applicationClient = applicationClient;
    }

    /**
     * Opens the conversation for an application, or returns the existing one.
     *
     * Only the client who posted the job may start it: a worker who applied to
     * thirty jobs cannot open thirty chats with thirty strangers.
     */
    @Transactional
    public Conversation open(UUID applicationId, UUID callerId) {
        Conversation existing = conversationRepository.findByApplicationId(applicationId).orElse(null);
        if (existing != null) {
            requireParticipant(existing, callerId);
            return existing;
        }

        ApplicationClient.ChatContext context = applicationClient.getChatContext(applicationId);
        if (!context.clientId().equals(callerId)) {
            throw new ChatException(HttpStatus.FORBIDDEN, "CONVERSATION_ACCESS_DENIED",
                    "Only the job owner can start this conversation");
        }

        Conversation conversation = new Conversation();
        conversation.setApplicationId(context.applicationId());
        conversation.setJobId(context.jobId());
        conversation.setClientId(context.clientId());
        conversation.setWorkerId(context.applicantId());

        try {
            return conversationRepository.saveAndFlush(conversation);
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint on application_id also covers two requests
            // racing: whoever lost simply reads the row the winner wrote.
            return conversationRepository.findByApplicationId(applicationId)
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional(readOnly = true)
    public List<Conversation> listForUser(UUID userId) {
        return conversationRepository.findByClientIdOrWorkerIdOrderByCreatedAtDesc(userId, userId);
    }

    @Transactional(readOnly = true)
    public Conversation getForParticipant(UUID conversationId, UUID userId) {
        Conversation conversation = get(conversationId);
        requireParticipant(conversation, userId);
        return conversation;
    }

    @Transactional(readOnly = true)
    public Page<Message> messages(UUID conversationId, UUID userId, int page, int size) {
        requireParticipant(get(conversationId), userId);

        return messageRepository.findByConversationId(
                conversationId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    /**
     * Stores a message and reports who should receive it. The recipient is
     * derived from the conversation, never taken from the sender's payload.
     */
    @Transactional
    public Delivery send(UUID conversationId, UUID senderId, String body) {
        Conversation conversation = get(conversationId);
        requireParticipant(conversation, senderId);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setBody(body);

        return new Delivery(messageRepository.save(message), conversation.otherParticipant(senderId));
    }

    private Conversation get(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ChatException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND",
                        "Conversation not found"));
    }

    private void requireParticipant(Conversation conversation, UUID userId) {
        if (!conversation.hasParticipant(userId)) {
            throw new ChatException(HttpStatus.FORBIDDEN, "CONVERSATION_ACCESS_DENIED",
                    "You are not part of this conversation");
        }
    }

    public record Delivery(Message message, UUID recipientId) {
    }
}
