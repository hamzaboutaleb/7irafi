package com.herafi.user_service.exception;

import java.util.UUID;

/**
 * Thrown when the user being saved has no ACTIVE professional profile.
 *
 * <p>Deliberately not a database constraint: professional status is mutable, so
 * a suspended profile would either invalidate existing rows or leave the
 * constraint stale.
 */
public class NotAProfessionalException extends RuntimeException {
    public NotAProfessionalException(UUID userId) {
        super("User is not an active professional: " + userId);
    }
}
