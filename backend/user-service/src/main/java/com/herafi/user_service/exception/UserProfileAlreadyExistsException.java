package com.herafi.user_service.exception;

public class UserProfileAlreadyExistsException extends RuntimeException {

    public UserProfileAlreadyExistsException() {
        super("User profile already exists");
    }
}
