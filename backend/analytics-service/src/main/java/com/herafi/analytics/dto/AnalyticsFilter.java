package com.herafi.analytics.dto;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

/** Required date range and optional chart filters. */
public record AnalyticsFilter(
        @NotNull @DateTimeFormat(iso = DATE_TIME) Instant from,
        @NotNull @DateTimeFormat(iso = DATE_TIME) Instant to,
        Granularity granularity,
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String skill,
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String city) {

    public AnalyticsFilter {
        granularity = requireNonNullElse(granularity, Granularity.DAY);
    }

    @AssertTrue(message = "from must be before to")
    public boolean isDateRangeValid() {
        // Missing dates are reported by @NotNull on the individual fields.
        return from == null || to == null || from.isBefore(to);
    }
}
