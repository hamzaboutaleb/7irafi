package com.herafi.auth_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.entity.RefreshToken;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.exception.InvalidRefreshTokenException;
import com.herafi.auth_service.repository.RefreshTokenRepository;
import com.herafi.auth_service.repository.UserCredentialRepository;

import lombok.extern.slf4j.Slf4j;

// Creates, swaps (rotates) and revokes refresh tokens.
@Slf4j
@Service
public class RefreshTokenService {

    // 32 random bytes = 256 bits: impossible to guess
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final UserCredentialRepository users;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            UserCredentialRepository users,
            @Value("${security.jwt.refresh-token-ttl}") Duration ttl) {
        this.repository = repository;
        this.users = users;
        this.ttl = ttl;
    }

    // The result of /auth/refresh: who the user is, and their new refresh token.
    public record Rotation(UserCredential user, String refreshToken) {
    }

    // login / register: make a new token, save its hash, return the raw value once
    @Transactional
    public String create(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.save(new RefreshToken(userId, hash(token), Instant.now().plus(ttl)));
        return token;
    }

    // /auth/refresh: the old token stops working and a new one is returned.
    // noRollbackFor: when a reused token is detected, the "revoke all" must still be saved.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String token) {
        Instant now = Instant.now();
        RefreshToken current = repository.findByTokenHash(hash(token))
                .orElseThrow(InvalidRefreshTokenException::new);

        // already used: someone else may have a copy, so log this user out everywhere
        if (current.getRevokedAt() != null) {
            repository.revokeAllForUser(current.getUserId(), now);
            log.atWarn().addKeyValue("event", "refresh_token_reused")
                    .addKeyValue("userId", current.getUserId())
                    .log("Refresh token used twice, all tokens of the user revoked");
            throw new InvalidRefreshTokenException();
        }

        if (!current.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        UserCredential user = users.findById(current.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return new Rotation(user, create(user.getId()));
    }

    // logout: this token stops working (an unknown token is simply ignored)
    @Transactional
    public void revoke(String token) {
        repository.findByTokenHash(hash(token))
                .ifPresent(found -> found.revoke(Instant.now()));
    }

    // SHA-256 as 64 hex characters; the same token always gives the same hash
    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is missing from this Java runtime", exception);
        }
    }
}
