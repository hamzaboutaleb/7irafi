package com.herafi.job_service.dto;

import java.util.UUID;

public record ChatEligibilityResponse(
    boolean allowed,
    UUID clientId
) {}
