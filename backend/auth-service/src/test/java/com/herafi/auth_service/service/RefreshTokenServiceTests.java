package com.herafi.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.exception.InvalidRefreshTokenException;
import com.herafi.auth_service.repository.UserCredentialRepository;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class RefreshTokenServiceTests {

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private UserCredentialRepository users;

    private UserCredential saveUser() {
        UserCredential user = new UserCredential();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        return users.saveAndFlush(user);
    }

    // a refresh gives a new, different token for the same user
    @Test
    void rotateReturnsNewTokenForSameUser() {
        UserCredential user = saveUser();
        String first = service.create(user.getId());

        RefreshTokenService.Rotation rotation = service.rotate(first);

        assertThat(rotation.user().getId()).isEqualTo(user.getId());
        assertThat(rotation.refreshToken()).isNotEqualTo(first);
    }

    // a used token cannot be used again, and its reuse also kills the newer token
    @Test
    void reusingOldTokenRevokesEveryToken() {
        UserCredential user = saveUser();
        String first = service.create(user.getId());
        String second = service.rotate(first).refreshToken();

        assertThatThrownBy(() -> service.rotate(first))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate(second))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    // a token we never issued is refused
    @Test
    void unknownTokenIsRefused() {
        assertThatThrownBy(() -> service.rotate("not-a-real-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    // after logout the token is refused
    @Test
    void revokedTokenIsRefused() {
        UserCredential user = saveUser();
        String token = service.create(user.getId());

        service.revoke(token);

        assertThatThrownBy(() -> service.rotate(token))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
