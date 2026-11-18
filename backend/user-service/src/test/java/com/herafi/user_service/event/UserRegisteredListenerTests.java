package com.herafi.user_service.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.exception.ProfileEmailAlreadyInUseException;
import com.herafi.user_service.service.UserProfileService;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class UserRegisteredListenerTests {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final UserProfileService service = mock(UserProfileService.class);
    private final UserRegisteredListener listener = new UserRegisteredListener(mapper, service);

    // a message from auth-service becomes a create-profile call
    @Test
    void createsProfileFromEvent() {
        UUID id = UUID.randomUUID();
        String payload = mapper.writeValueAsString(
                new UserRegisteredEvent(id, "user@example.com", "Youssef", "Rachidi"));

        listener.onMessage(payload);

        verify(service).createIfAbsent(new CreateUserProfileRequest(
                id, "user@example.com", "Youssef", "Rachidi"));
    }

    // an email owned by another account is logged and skipped, not retried
    @Test
    void skipsEventWhenEmailBelongsToAnotherAccount() {
        when(service.createIfAbsent(any())).thenThrow(new ProfileEmailAlreadyInUseException());
        String payload = mapper.writeValueAsString(
                new UserRegisteredEvent(UUID.randomUUID(), "user@example.com", "Youssef", "Rachidi"));

        assertDoesNotThrow(() -> listener.onMessage(payload));
    }
}
