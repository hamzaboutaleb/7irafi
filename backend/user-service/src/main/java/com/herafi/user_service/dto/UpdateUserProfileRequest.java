package com.herafi.user_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String firstName,

        @NotBlank
        @Size(min = 3, max = 50)
        String lastName
) {
}
