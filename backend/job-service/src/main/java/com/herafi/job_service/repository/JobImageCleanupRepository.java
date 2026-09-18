package com.herafi.job_service.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.job_service.entity.JobImageCleanup;

public interface JobImageCleanupRepository extends JpaRepository<JobImageCleanup, UUID> {

    @Query(value = "SELECT * FROM job_image_cleanup WHERE id = :id FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<JobImageCleanup> findByIdForCleanup(@Param("id") UUID id);

    @Query(value = """
            SELECT * FROM job_image_cleanup
            WHERE next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<JobImageCleanup> findNextForCleanup(@Param("now") Instant now);
}
