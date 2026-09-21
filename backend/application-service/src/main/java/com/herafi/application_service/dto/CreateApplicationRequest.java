package com.herafi.application_service.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record CreateApplicationRequest(@NotNull UUID jobId) {
}
