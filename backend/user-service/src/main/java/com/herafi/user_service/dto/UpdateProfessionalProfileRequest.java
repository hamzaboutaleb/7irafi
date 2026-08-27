package com.herafi.user_service.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record UpdateProfessionalProfileRequest(
        @NotBlank @Size(min = 10, max = 2000) String biography,
        @NotEmpty Set<@NotBlank String> categoryCodes,
        @NotEmpty Set<@NotBlank String> cityCodes) {
}
