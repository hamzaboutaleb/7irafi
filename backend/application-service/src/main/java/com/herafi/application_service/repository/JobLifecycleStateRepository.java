package com.herafi.application_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.application_service.entity.JobLifecycleState;

import jakarta.persistence.LockModeType;

public interface JobLifecycleStateRepository extends JpaRepository<JobLifecycleState, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO job_lifecycle_state (job_id, job_version, deleted)
            VALUES (:jobId, -1, FALSE) ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("jobId") UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM JobLifecycleState state WHERE state.jobId = :jobId")
    JobLifecycleState lockByJobId(@Param("jobId") UUID jobId);
}
