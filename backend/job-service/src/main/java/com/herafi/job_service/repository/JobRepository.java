package com.herafi.job_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.job_service.entity.Job;

public interface JobRepository extends JpaRepository<Job, UUID> {
}
