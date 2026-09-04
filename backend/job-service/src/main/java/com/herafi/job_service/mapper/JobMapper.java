package com.herafi.job_service.mapper;

import java.util.UUID;
import java.util.List;

import org.springframework.stereotype.Component;

import com.herafi.job_service.dto.JobListResponse;
import com.herafi.job_service.dto.JobImageResponse;
import com.herafi.job_service.dto.JobResponse;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.service.ImageStorageService;

@Component
public class JobMapper {

    private final ImageStorageService imageStorageService;

    public JobMapper(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    public JobListResponse toListResponse(Job job) {
        return new JobListResponse(
                job.getId(),
                job.getClientId(),
                job.getTitle(),
                job.getDescription(),
                job.getSkill(),
                job.getCity(),
                job.getBudget(),
                job.getEstimatedDuration(),
                job.getPreferredTime(),
                job.getStatus(),
                job.getAssignedWorkerId(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    public JobResponse toDetailResponse(Job job, UUID viewerId) {
        return new JobResponse(
                job.getId(),
                job.getClientId(),
                job.getTitle(),
                job.getDescription(),
                job.getSkill(),
                job.getCity(),
                canViewAddress(job, viewerId) ? job.getAddressLine() : null,
                job.getBudget(),
                job.getEstimatedDuration(),
                job.getPreferredTime(),
                job.getStatus(),
                job.getAssignedWorkerId(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getImages().stream()
                        .map(image -> new JobImageResponse(
                                image.getId(),
                                imageStorageService.generateDownloadUrl(image.getObjectKey())))
                        .toList()
        );
    }

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
                job.getUpdatedAt(),
                List.of()
        );
    }

    private boolean canViewAddress(Job job, UUID viewerId) {
        return job.getClientId().equals(viewerId)
                || viewerId.equals(job.getAssignedWorkerId());
    }
}
