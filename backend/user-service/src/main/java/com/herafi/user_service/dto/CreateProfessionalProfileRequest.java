package com.herafi.user_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProfessionalProfileRequest(
        @NotBlank
        @Size(max = 2000)
        String biography
) {
}
