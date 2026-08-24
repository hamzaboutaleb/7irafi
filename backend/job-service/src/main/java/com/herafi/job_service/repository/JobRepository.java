package com.herafi.job_service.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.herafi.job_service.entity.Job;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByClientId(UUID clientId);

    @Query("SELECT DISTINCT j.skill FROM Job j")
    List<String> findAllSkills();

    @Query("SELECT DISTINCT j.city FROM Job j")
    List<String> findAllCities();

}
