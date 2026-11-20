package com.herafi.auth_service.event;

import java.util.UUID;

// The message sent on "users.registered" so user-service creates the profile.
// user-service has the same record: keep the fields the same on both sides.
public record UserRegisteredEvent(
        UUID userId,
        String email,
        String firstName,
        String lastName) {

    public static final String TOPIC = "users.registered";
}
