package com.herafi.user_service.exception;

import java.util.UUID;

public class ProfessionalProfileNotFoundException extends RuntimeException {
    public ProfessionalProfileNotFoundException(UUID userId) {
        super("Professional profile not found: " + userId);
    }
}
