package com.herafi.job_service.dto;

import java.util.UUID;

import com.herafi.job_service.entity.JobStatus;

public record JobSummaryResponse (
    UUID id,
    UUID clientId,
    JobStatus status,
    UUID assignedWorkerId
) {

}
