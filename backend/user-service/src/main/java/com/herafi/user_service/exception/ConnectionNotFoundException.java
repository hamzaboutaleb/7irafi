package com.herafi.user_service.exception;

import java.util.UUID;

/** Thrown when removing a connection that is not in the caller's network. */
public class ConnectionNotFoundException extends RuntimeException {
    public ConnectionNotFoundException(UUID connectionId) {
        super("Connection not found: " + connectionId);
    }
}
