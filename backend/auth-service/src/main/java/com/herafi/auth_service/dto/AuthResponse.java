package com.herafi.auth_service.dto;

import java.util.UUID;

import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;

// Returned by register, login and refresh.
// refreshToken: send it to /auth/refresh to get a new pair when accessToken expires.
public record AuthResponse
(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    UserSummary user
) {
    public static AuthResponse from(
        UserCredential credential,
        String accessToken,
        long expiresIn,
        String refreshToken ) {
            return new AuthResponse(
                accessToken,
                "Bearer",
                expiresIn,
                refreshToken,
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
