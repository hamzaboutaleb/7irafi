package com.herafi.auth_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.entity.RefreshToken;
import com.herafi.auth_service.entity.UserCredential;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class RefreshTokenRepositoryTests {

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UserCredentialRepository users;

    // refresh_tokens.user_id must point to a real user
    private UUID saveUser(String email) {
        UserCredential user = new UserCredential();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hash");
        return users.saveAndFlush(user).getId();
    }

    // a saved token can be found again by its hash
    @Test
    void findsTokenByHash() {
        UUID userId = saveUser("find@example.com");
        repository.save(new RefreshToken(userId, "a".repeat(64), Instant.now().plusSeconds(60)));

        assertThat(repository.findByTokenHash("a".repeat(64)))
                .get()
                .extracting(RefreshToken::getUserId)
                .isEqualTo(userId);
        assertThat(repository.findByTokenHash("b".repeat(64))).isEmpty();
    }

    // revoking all tokens of one user leaves other users alone
    @Test
    void revokesAllTokensOfOneUser() {
        UUID alice = saveUser("alice@example.com");
        UUID bob = saveUser("bob@example.com");
        Instant later = Instant.now().plusSeconds(60);
        repository.save(new RefreshToken(alice, "1".repeat(64), later));
        repository.save(new RefreshToken(alice, "2".repeat(64), later));
        repository.save(new RefreshToken(bob, "3".repeat(64), later));

        int revoked = repository.revokeAllForUser(alice, Instant.now());

        assertThat(revoked).isEqualTo(2);
        assertThat(repository.findByTokenHash("1".repeat(64)).get().isActive(Instant.now())).isFalse();
        assertThat(repository.findByTokenHash("3".repeat(64)).get().isActive(Instant.now())).isTrue();
    }
}
