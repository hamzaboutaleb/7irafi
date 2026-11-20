package com.herafi.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.entity.OutboxEvent;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.event.UserRegisteredEvent;
import com.herafi.auth_service.exception.EmailAlreadyInUseException;
import com.herafi.auth_service.repository.OutboxEventRepository;

import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class RegistrationServiceTests {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper mapper;

    // registering saves the account AND one "users.registered" message
    @Test
    void registerSavesOutboxMessage() {
        UserCredential saved = registrationService.register(
                new RegisterRequest(" New@Example.com ", "password123", " Youssef ", " Rachidi "));

        List<OutboxEvent> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);

        OutboxEvent message = messages.get(0);
        assertThat(message.getTopic()).isEqualTo("users.registered");
        assertThat(message.getMessageKey()).isEqualTo(saved.getId().toString());
        assertThat(message.getPublishedAt()).isNull();

        UserRegisteredEvent event = mapper.readValue(message.getPayload(), UserRegisteredEvent.class);
        assertThat(event).isEqualTo(
                new UserRegisteredEvent(saved.getId(), "new@example.com", "Youssef", "Rachidi"));
    }

    // a taken email saves nothing, not even a message
    @Test
    void duplicateEmailSavesNoMessage() {
        registrationService.register(
                new RegisterRequest("taken@example.com", "password123", "Youssef", "Rachidi"));

        assertThatThrownBy(() -> registrationService.register(
                new RegisterRequest("taken@example.com", "password123", "Other", "Person")))
                .isInstanceOf(EmailAlreadyInUseException.class);

        assertThat(outboxRepository.findAll()).hasSize(1);
    }
}
