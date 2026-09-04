package com.herafi.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.PreferredTime;

public record JobListResponse(
        UUID id,
        UUID clientId,
        String title,
        String description,
        String skill,
        String city,
        BigDecimal budget,
        EstimatedDuration estimatedDuration,
        PreferredTime preferredTime,
        JobStatus status,
        UUID assignedWorkerId,
        Instant createdAt,
        Instant updatedAt
) {
}
