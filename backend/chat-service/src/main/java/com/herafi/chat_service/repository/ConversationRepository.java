package com.herafi.chat_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.chat_service.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Opening a conversation twice must find the first one, not create a second. */
    Optional<Conversation> findByApplicationId(UUID applicationId);

    /**
     * "My conversations", from both sides: the same person is the client on the
     * jobs they posted and the worker on the ones they applied to.
     */
    List<Conversation> findByClientIdOrWorkerIdOrderByCreatedAtDesc(UUID clientId, UUID workerId);
}
