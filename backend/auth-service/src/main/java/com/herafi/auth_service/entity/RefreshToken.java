package com.herafi.auth_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// One refresh token (table created by V3__create_refresh_tokens.sql).
// Only the hash is saved, never the token itself.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // empty = still usable
    private Instant revokedAt;

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    // usable = not revoked and not expired
    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    // used once, or logged out: never usable again
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
