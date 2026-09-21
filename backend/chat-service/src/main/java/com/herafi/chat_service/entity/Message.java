package com.herafi.chat_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One message inside a conversation.
 *
 * conversationId and senderId are plain ids rather than associations: a message
 * never needs to drag a whole Conversation around, and the sender lives in
 * another service entirely.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "messages")
public class Message {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private UUID senderId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
