package com.herafi.job_service.dto;

import java.util.UUID;

public record JobImageResponse(
    UUID id,
    String url
) {
}
