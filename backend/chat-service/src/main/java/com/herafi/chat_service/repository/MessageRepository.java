package com.herafi.chat_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.chat_service.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * History for one conversation. The caller passes the sort (newest first,
     * id as the tie-breaker) so it matches the index on the table.
     */
    Page<Message> findByConversationId(UUID conversationId, Pageable pageable);
}
