package com.herafi.auth_service.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.dto.CreateUserProfileRequest;
import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.exception.EmailAlreadyInUseException;
import com.herafi.auth_service.exception.UserServiceUnavailableException;
import com.herafi.auth_service.repository.UserCredentialRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RegistrationService {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileClient userProfileClient;

    public RegistrationService(
            UserCredentialRepository repository,
            PasswordEncoder passwordEncoder,
            UserProfileClient userProfileClient) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.userProfileClient = userProfileClient;
    }

    @Transactional
    public UserCredential register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (repository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException();
        }

        UserCredential credential = new UserCredential();
        credential.setId(UUID.randomUUID());
        credential.setEmail(email);
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credential.setRole(Role.USER);

        UserCredential savedCredential = repository.saveAndFlush(credential);

        try {
            userProfileClient.createProfile(new CreateUserProfileRequest(
                    savedCredential.getId(),
                    savedCredential.getEmail(),
                    request.firstName().trim(),
                    request.lastName().trim()));
        } catch (UserServiceUnavailableException exception) {
            log.atWarn().addKeyValue("event", "registration_failed").setCause(exception)
                    .log("Registration failed, user profile creation failed");
            throw exception;
        }

        return savedCredential;
    }
}
