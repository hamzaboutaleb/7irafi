package com.herafi.user_service.exception;

/**
 * Thrown when a user tries to save themselves to their own network.
 *
 * <p>The database enforces this too, via chk_user_connections_not_self. This
 * exists so the caller gets a clear 400 instead of a constraint violation
 * surfacing as a 500.
 */
public class SelfConnectionException extends RuntimeException {
    public SelfConnectionException() {
        super("You cannot add yourself to your own network");
    }
}
