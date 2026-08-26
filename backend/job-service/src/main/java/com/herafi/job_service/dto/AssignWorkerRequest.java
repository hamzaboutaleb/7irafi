package com.herafi.job_service.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;


public record AssignWorkerRequest(
    @NotNull UUID workerId
) {}