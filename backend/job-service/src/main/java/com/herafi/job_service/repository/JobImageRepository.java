package com.herafi.job_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.herafi.job_service.entity.JobImage;

public interface JobImageRepository extends JpaRepository<JobImage, UUID> {

    List<JobImage> findByJob_IdOrderByCreatedAtAscIdAsc(UUID jobId);

    long countByJob_Id(UUID jobId);

    @Query("SELECT image FROM JobImage image "
            + "WHERE image.job.deleted = true "
            + "ORDER BY image.createdAt ASC, image.id ASC")
    List<JobImage> findImagesForDeletedJobs();
}
