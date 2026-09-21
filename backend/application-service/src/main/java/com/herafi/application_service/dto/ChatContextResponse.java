package com.herafi.application_service.dto;

import java.util.UUID;

public record ChatContextResponse(
        UUID applicationId,
        UUID jobId,
        UUID applicantId,
        UUID clientId,
        String status) {
}
