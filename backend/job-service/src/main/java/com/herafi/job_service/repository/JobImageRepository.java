package com.herafi.job_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.job_service.entity.JobImage;

public interface JobImageRepository extends JpaRepository<JobImage, UUID> {

    List<JobImage> findByJob_IdOrderByCreatedAtAscIdAsc(UUID jobId);

    interface Thumbnail {
        UUID getJobId();
        String getObjectKey();
    }

    @Query("""
            SELECT image.job.id AS jobId, image.objectKey AS objectKey
            FROM JobImage image
            WHERE image.job.id IN (:jobIds)
              AND NOT EXISTS (
                  SELECT earlier.id FROM JobImage earlier
                  WHERE earlier.job.id = image.job.id
                    AND (earlier.createdAt < image.createdAt
                         OR (earlier.createdAt = image.createdAt AND earlier.id < image.id))
              )
            """)
    List<Thumbnail> findThumbnailsByJobIds(@Param("jobIds") List<UUID> jobIds);

    long countByJob_Id(UUID jobId);

    boolean existsByObjectKey(String objectKey);

    @Query("SELECT image.objectKey FROM JobImage image WHERE image.job.id = :jobId")
    List<String> findObjectKeysByJobId(@Param("jobId") UUID jobId);
}
