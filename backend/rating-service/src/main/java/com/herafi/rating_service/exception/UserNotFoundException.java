package com.herafi.rating_service.exception;

import java.util.UUID;

public class UserNotFoundException extends RatingException {

    public UserNotFoundException(UUID userId) {
        super("USER_NOT_FOUND", "User not found: " + userId);
    }
}