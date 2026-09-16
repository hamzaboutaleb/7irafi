package com.herafi.application_service.dto;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobId, UUID applicantId,
        Instant createdAt, String status) {

}
