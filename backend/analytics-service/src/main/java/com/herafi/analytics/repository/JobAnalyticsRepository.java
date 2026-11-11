package com.herafi.analytics.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsFilter;
import com.herafi.analytics.dto.AnalyticsResponse;
import com.herafi.analytics.dto.AnalyticsResponse.Count;
import com.herafi.analytics.dto.AnalyticsResponse.JobRecord;
import com.herafi.analytics.dto.JobStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JobAnalyticsRepository {
    private static final String FILTER = """
             FROM analytics_jobs
             WHERE client_id = :owner
               AND created_at >= :from AND created_at < :to
               AND (:status IS NULL OR status = :status)
               AND (:skill IS NULL OR skill = :skill)
               AND (:city IS NULL OR city = :city)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JobAnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsResponse<JobRecord> summarize(UUID owner, AnalyticsFilter filter, JobStatus status) {
        var parameters = AnalyticsParameters.of(owner, filter, status);
        long total = jdbc.queryForObject("SELECT count(*)" + FILTER, parameters, Long.class);

        var distribution = jdbc.query("SELECT status, count(*)" + FILTER + " GROUP BY status ORDER BY status",
                parameters, (rs, row) -> new Count(rs.getString(1), rs.getLong(2)));

        var activity = jdbc.query("""
                SELECT date_trunc(:granularity, created_at AT TIME ZONE 'Africa/Casablanca'), count(*)
                """ + FILTER + " GROUP BY 1 ORDER BY 1",
                parameters, (rs, row) -> new Count(rs.getObject(1, LocalDateTime.class).toString(), rs.getLong(2)));

        var records = jdbc.query("""
                SELECT job_id, client_id, status, skill, city, created_at
                """ + FILTER + " ORDER BY created_at DESC, job_id DESC LIMIT :size OFFSET :offset",
                parameters, (rs, row) -> new JobRecord(
                        rs.getObject("job_id", UUID.class), rs.getObject("client_id", UUID.class),
                        JobStatus.valueOf(rs.getString("status")), rs.getString("skill"), rs.getString("city"),
                        rs.getTimestamp("created_at").toInstant()));

        return new AnalyticsResponse<>(total, distribution, activity, records, filter.page(), filter.size());
    }
}
