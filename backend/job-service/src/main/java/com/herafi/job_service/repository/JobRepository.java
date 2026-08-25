package com.herafi.job_service.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.herafi.job_service.entity.Job;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    List<Job> findByClientId(UUID clientId);

    List<Job> findByClientIdAndDeletedFalse(UUID clientId);

    @Query("SELECT DISTINCT j.skill FROM Job j")
    List<String> findAllSkills();

    @Query("SELECT DISTINCT j.city FROM Job j")
    List<String> findAllCities();

    Optional<Job> findByIdAndDeletedFalse(UUID id);

    List<Job> findAllByDeletedFalse();

}
