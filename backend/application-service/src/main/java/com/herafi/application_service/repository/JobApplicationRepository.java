package com.herafi.application_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.application_service.entity.JobApplication;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    boolean existsByJobIdAndApplicantId(UUID jobId, UUID applicantId);

    Page<JobApplication> findByApplicantId(UUID applicantId, Pageable pageable);

    Page<JobApplication> findByJobId(UUID jobId, Pageable pageable);

    List<JobApplication> findAllByJobId(UUID jobId);
}
