package com.herafi.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /auth/refresh and POST /auth/logout.
public record RefreshTokenRequest(
    @NotBlank
    @Size(max = 128)
    String refreshToken
) {

}
