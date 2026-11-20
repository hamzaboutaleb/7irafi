package com.herafi.auth_service.exception;

// Refresh token unknown, expired, or already used: the user must log in again.
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
