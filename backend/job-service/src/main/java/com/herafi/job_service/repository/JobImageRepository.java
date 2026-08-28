package com.herafi.job_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.job_service.entity.JobImage;

public interface JobImageRepository extends JpaRepository<JobImage, UUID> {

    List<JobImage> findByJobIdOrderByPositionAsc(UUID jobId);
}