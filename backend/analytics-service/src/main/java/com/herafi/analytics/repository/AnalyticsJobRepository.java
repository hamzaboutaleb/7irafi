package com.herafi.analytics.repository;

import com.herafi.analytics.entity.AnalyticsJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsJobRepository extends JpaRepository<AnalyticsJob, UUID> {

    @Query(value = """
        SELECT 
            CAST(DATE_TRUNC(:granularity, created_at AT TIME ZONE 'Africa/Casablanca') AS VARCHAR) as period_start,
            COUNT(job_id) as count
        FROM analytics_jobs
        WHERE client_id = :userId
          AND created_at >= :from
          AND created_at < :to
          AND (:status IS NULL OR status = :status)
          AND (:skill IS NULL OR skill = :skill)
          AND (:city IS NULL OR city = :city)
        GROUP BY DATE_TRUNC(:granularity, created_at AT TIME ZONE 'Africa/Casablanca')
        ORDER BY period_start ASC
        """, nativeQuery = true)
    List<Object[]> getActivityBuckets(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") String status,
            @Param("skill") String skill,
            @Param("city") String city,
            @Param("granularity") String granularity);
}
