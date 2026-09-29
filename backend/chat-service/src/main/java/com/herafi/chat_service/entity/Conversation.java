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
 * The two people allowed to talk about one job application.
 *
 * The ids are copied from application-service when the conversation is opened
 * and never refreshed: being hired is a fact that does not get undone.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID applicationId;

    @Column(nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, updatable = false)
    private UUID clientId;

    @Column(nullable = false, updatable = false)
    private UUID workerId;

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

    public boolean hasParticipant(UUID userId) {
        return clientId.equals(userId) || workerId.equals(userId);
    }

    public UUID otherParticipant(UUID userId) {
        return clientId.equals(userId) ? workerId : clientId;
    }
}
