package com.herafi.user_service.exception;

import java.util.UUID;

public class ProfessionalProfileAlreadyExistsException extends RuntimeException {
    public ProfessionalProfileAlreadyExistsException(UUID userId) {
        super("Professional profile already exists: " + userId);
    }
}
