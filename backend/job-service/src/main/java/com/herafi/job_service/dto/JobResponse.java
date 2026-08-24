package com.herafi.job_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;
import com.herafi.job_service.entity.PreferredTime;

public record JobResponse(
        UUID id,
        UUID clientId,
        String title,
        String description,
        String skill,
        String city,
        String addressLine,
        BigDecimal budget,
        EstimatedDuration estimatedDuration,
        PreferredTime preferredTime,
        JobStatus status,
        UUID assignedWorkerId,
        Instant createdAt,
        Instant updatedAt
) {

        public static JobResponse from(Job job) {
                return new JobResponse(
                                job.getId(),
                                job.getClientId(),
                                job.getTitle(),
                                job.getDescription(),
                                job.getSkill(),
                                job.getCity(),
                                job.getAddressLine(),
                                job.getBudget(),
                                job.getEstimatedDuration(),
                                job.getPreferredTime(),
                                job.getStatus(),
                                job.getAssignedWorkerId(),
                                job.getCreatedAt(),
                                job.getUpdatedAt());
        }

        
}