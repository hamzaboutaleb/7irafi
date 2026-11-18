package com.herafi.user_service.event;

import java.util.UUID;

// The message auth-service sends on "users.registered" after a new account is saved.
public record UserRegisteredEvent(
        UUID userId,
        String email,
        String firstName,
        String lastName) {
}
