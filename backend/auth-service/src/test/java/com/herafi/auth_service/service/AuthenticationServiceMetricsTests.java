package com.herafi.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import com.herafi.auth_service.dto.LoginRequest;
import com.herafi.auth_service.exception.InvalidCredentialsException;
import com.herafi.auth_service.repository.UserCredentialRepository;

class AuthenticationServiceMetricsTests {

    @Test
    void loginFailureIncrementsTheCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        UserCredentialRepository repository = mock(UserCredentialRepository.class);

        AuthenticationService service = new AuthenticationService(
                authenticationManager, repository, registry);

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("user@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(registry.counter("herafi.login.failures").count()).isEqualTo(1.0);
    }
}
