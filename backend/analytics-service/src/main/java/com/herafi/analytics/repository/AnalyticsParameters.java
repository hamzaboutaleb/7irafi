package com.herafi.analytics.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class AnalyticsParameters {
    private AnalyticsParameters() { }

    static MapSqlParameterSource of(UUID owner, AnalyticsFilter filter, Enum<?> status) {
        // Explicit types let PostgreSQL bind absent optional filters.
        return new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("from", Timestamp.from(filter.from()))
                .addValue("to", Timestamp.from(filter.to()))
                .addValue("granularity", filter.granularity().sqlValue())
                .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
                .addValue("skill", filter.skill(), Types.VARCHAR)
                .addValue("city", filter.city(), Types.VARCHAR)
                .addValue("size", filter.size())
                .addValue("offset", filter.offset());
    }
}
