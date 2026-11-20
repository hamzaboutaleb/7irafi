package com.herafi.user_service.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.exception.ProfileEmailAlreadyInUseException;
import com.herafi.user_service.service.UserProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// Creates the profile when auth-service says a new account was registered.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "users.registration-listener", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UserRegisteredListener {

    private final ObjectMapper mapper;
    private final UserProfileService userProfileService;

    // one Kafka message = one new account
    @KafkaListener(id = "user-registered", topics = "users.registered",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        UserRegisteredEvent event = mapper.readValue(payload, UserRegisteredEvent.class);
        CreateUserProfileRequest request = new CreateUserProfileRequest(
                event.userId(),
                event.email(),
                event.firstName(),
                event.lastName());

        try {
            userProfileService.createIfAbsent(request);
        } catch (ProfileEmailAlreadyInUseException exception) {
            // retrying will never fix this, so log it and move on to the next message
            log.error("Skipped users.registered event: email already used by another profile, userId={}",
                    event.userId());
        }
    }
}
