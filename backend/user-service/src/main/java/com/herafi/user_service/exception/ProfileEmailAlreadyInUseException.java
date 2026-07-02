package com.herafi.user_service.exception;

public class ProfileEmailAlreadyInUseException extends RuntimeException {

    public ProfileEmailAlreadyInUseException() {
        super("Email already belongs to another profile");
    }
}
