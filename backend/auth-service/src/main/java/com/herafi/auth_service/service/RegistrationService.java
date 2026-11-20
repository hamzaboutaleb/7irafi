package com.herafi.auth_service.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.entity.OutboxEvent;
import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.event.UserRegisteredEvent;
import com.herafi.auth_service.exception.EmailAlreadyInUseException;
import com.herafi.auth_service.repository.OutboxEventRepository;
import com.herafi.auth_service.repository.UserCredentialRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class RegistrationService {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper mapper;

    public RegistrationService(
            UserCredentialRepository repository,
            PasswordEncoder passwordEncoder,
            OutboxEventRepository outboxRepository,
            ObjectMapper mapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.outboxRepository = outboxRepository;
        this.mapper = mapper;
    }

    // Saves the account and the "users.registered" message in ONE transaction:
    // both are saved or neither is. OutboxRelay sends the message to Kafka later.
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

        UserRegisteredEvent event = new UserRegisteredEvent(
                savedCredential.getId(),
                savedCredential.getEmail(),
                request.firstName().trim(),
                request.lastName().trim());
        outboxRepository.save(new OutboxEvent(
                UserRegisteredEvent.TOPIC,
                savedCredential.getId().toString(),
                mapper.writeValueAsString(event)));

        return savedCredential;
    }
}
