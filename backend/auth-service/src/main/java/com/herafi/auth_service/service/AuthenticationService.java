package com.herafi.auth_service.service;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.herafi.auth_service.dto.LoginRequest;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.exception.InvalidCredentialsException;
import com.herafi.auth_service.repository.UserCredentialRepository;
import com.herafi.auth_service.security.UserPrincipal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final UserCredentialRepository repository;
    private final MeterRegistry meterRegistry;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserCredentialRepository repository,
            MeterRegistry meterRegistry) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public UserCredential authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            email,
                            request.password()
                    )
            );

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            return repository.findById(principal.id())
                    .orElseThrow(InvalidCredentialsException::new);
        } catch (AuthenticationException exception) {
            Counter.builder("herafi.login.failures")
                    .description("Login failures")
                    .register(meterRegistry)
                    .increment();
            log.atWarn().addKeyValue("event", "login_failed").log("Login failed");
            throw new InvalidCredentialsException();
        }
    }
}
