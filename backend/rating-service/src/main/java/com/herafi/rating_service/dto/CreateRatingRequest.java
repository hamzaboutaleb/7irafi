package com.herafi.rating_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRatingRequest(
        @NotNull UUID jobId,
        @NotNull UUID rateeId,
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 1000) String comment) {
}