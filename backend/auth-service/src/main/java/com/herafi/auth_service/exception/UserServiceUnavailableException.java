package com.herafi.auth_service.exception;

public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(Throwable cause) {
        super("User profile service is unavailable", cause);
    }
}
