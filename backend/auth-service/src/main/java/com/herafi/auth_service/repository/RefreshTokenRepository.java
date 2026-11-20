package com.herafi.auth_service.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.auth_service.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // find a token by the hash of what the browser sent
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // logout everywhere / stolen token: revoke every usable token of one user
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken token
            SET token.revokedAt = :now
            WHERE token.userId = :userId AND token.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
