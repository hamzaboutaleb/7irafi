package com.herafi.auth_service.dto;

import java.util.UUID;

public record CreateUserProfileRequest(
    UUID id,
    String email,
    String firstName,
    String lastName
) {
    
}
