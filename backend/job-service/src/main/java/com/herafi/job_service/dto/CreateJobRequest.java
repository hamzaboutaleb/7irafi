package com.herafi.job_service.dto;

import java.math.BigDecimal;

import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.PreferredTime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @NotBlank
        @Size(max = 120)
        String title,

        @NotBlank
        String description,

        @NotBlank
        @Size(max = 40)
        String skill,

        @NotBlank
        @Size(max = 40)
        String city,

        @Size(max = 200)
        String addressLine,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 8, fraction = 2,
                message = "Budget must have at most 8 integer digits and 2 decimal places")
        BigDecimal budget,

        @NotNull
        EstimatedDuration estimatedDuration,

        PreferredTime preferredTime
) {
}
