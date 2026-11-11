package com.herafi.notification_service.entity;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID recipientId;

    @Column(nullable = false, updatable = false, length = 60)
    private String type;

    @Column(nullable = false, updatable = false, length = 150)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String body;

    @Column(updatable = false, length = 300)
    private String targetUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Null until the recipient has seen it. */
    private Instant readAt;

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
