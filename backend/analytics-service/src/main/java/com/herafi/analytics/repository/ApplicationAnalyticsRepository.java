package com.herafi.analytics.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsFilter;
import com.herafi.analytics.dto.AnalyticsResponse;
import com.herafi.analytics.dto.AnalyticsResponse.ApplicationRecord;
import com.herafi.analytics.dto.AnalyticsResponse.Count;
import com.herafi.analytics.dto.ApplicationStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationAnalyticsRepository {
    private static final String FILTER = """
             FROM analytics_applications
             WHERE applicant_id = :owner
               AND submitted_at >= :from AND submitted_at < :to
               AND (:status IS NULL OR status = :status)
               AND (:skill IS NULL OR job_skill = :skill)
               AND (:city IS NULL OR job_city = :city)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ApplicationAnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsResponse<ApplicationRecord> summarize(UUID owner, AnalyticsFilter filter, ApplicationStatus status) {
        var parameters = AnalyticsParameters.of(owner, filter, status);
        long total = jdbc.queryForObject("SELECT count(*)" + FILTER, parameters, Long.class);

        var distribution = jdbc.query("SELECT status, count(*)" + FILTER + " GROUP BY status ORDER BY status",
                parameters, (rs, row) -> new Count(rs.getString(1), rs.getLong(2)));

        var activity = jdbc.query("""
                SELECT date_trunc(:granularity, submitted_at AT TIME ZONE 'Africa/Casablanca'), count(*)
                """ + FILTER + " GROUP BY 1 ORDER BY 1",
                parameters, (rs, row) -> new Count(rs.getObject(1, LocalDateTime.class).toString(), rs.getLong(2)));

        var records = jdbc.query("""
                SELECT application_id, job_id, applicant_id, status, job_skill, job_city, submitted_at
                """ + FILTER + " ORDER BY submitted_at DESC, application_id DESC LIMIT :size OFFSET :offset",
                parameters, (rs, row) -> new ApplicationRecord(
                        rs.getObject("application_id", UUID.class), rs.getObject("job_id", UUID.class),
                        rs.getObject("applicant_id", UUID.class), ApplicationStatus.valueOf(rs.getString("status")),
                        rs.getString("job_skill"), rs.getString("job_city"), rs.getTimestamp("submitted_at").toInstant()));

        return new AnalyticsResponse<>(total, distribution, activity, records, filter.page(), filter.size());
    }
}
