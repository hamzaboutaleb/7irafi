package com.herafi.job_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateJobRequest(
        @NotBlank
        @Size(max = 120)
        String title,

        @NotBlank
        String description,

        @Size(max = 200)
        String addressLine
) {}
