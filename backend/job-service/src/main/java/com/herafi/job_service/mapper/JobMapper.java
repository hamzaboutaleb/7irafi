package com.herafi.job_service.mapper;

import org.springframework.stereotype.Component;

import com.herafi.job_service.dto.JobResponse;
import com.herafi.job_service.entity.Job;

@Component
public class JobMapper {

    public JobResponse toResponse(Job job) {
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
                job.getUpdatedAt()
        );
    }
}