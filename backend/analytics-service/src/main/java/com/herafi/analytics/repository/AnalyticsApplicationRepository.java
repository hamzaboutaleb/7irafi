package com.herafi.analytics.repository;

import com.herafi.analytics.entity.AnalyticsApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsApplicationRepository extends JpaRepository<AnalyticsApplication, UUID> {

    @Query(value = """
        SELECT 
            CAST(DATE_TRUNC(:granularity, submitted_at AT TIME ZONE 'Africa/Casablanca') AS VARCHAR) as period_start,
            COUNT(application_id) as count
        FROM analytics_applications
        WHERE applicant_id = :userId
          AND submitted_at >= :from
          AND submitted_at < :to
          AND (:status IS NULL OR status = :status)
          AND (:jobSkill IS NULL OR job_skill = :jobSkill)
          AND (:jobCity IS NULL OR job_city = :jobCity)
        GROUP BY DATE_TRUNC(:granularity, submitted_at AT TIME ZONE 'Africa/Casablanca')
        ORDER BY period_start ASC
        """, nativeQuery = true)
    List<Object[]> getActivityBuckets(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") String status,
            @Param("jobSkill") String jobSkill,
            @Param("jobCity") String jobCity,
            @Param("granularity") String granularity);
}
