package com.herafi.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

public record AnalyticsResponse<T>(
        long total, List<Count> statusDistribution, List<Count> activity,
        List<T> records, int page, int size) {

    public record Count(String value, long count) { }

    // Preserve the existing response field names without exposing JDBC result maps.
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record JobRecord(UUID jobId, UUID clientId, JobStatus status, String skill, String city,
                            Instant createdAt) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ApplicationRecord(UUID applicationId, UUID jobId, UUID applicantId, ApplicationStatus status,
                                    String jobSkill, String jobCity, Instant submittedAt) { }
}
