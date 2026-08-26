package com.herafi.auth_service.dto;

import java.util.UUID;

import com.herafi.auth_service.entity.Role;

import com.herafi.auth_service.entity.UserCredential;

public record AuthResponse
(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserSummary user
) {
    public static AuthResponse from(
        UserCredential credential,
        String accessToken,
        long expiresIn ) {
            return new AuthResponse(
                accessToken,
                "Bearer", 
                expiresIn, 
                new UserSummary(
                    credential.getId(),
                    credential.getEmail(),
                    credential.getRole()
                ));
        }

    public record UserSummary(
        UUID id,
        String email,
        Role role
    ) {}
    
}
