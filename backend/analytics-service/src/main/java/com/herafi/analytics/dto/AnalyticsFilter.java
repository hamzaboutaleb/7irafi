package com.herafi.analytics.dto;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME;

/** Common HTTP filters. Defaults are applied once, before validation and querying. */
public record AnalyticsFilter(
        @DateTimeFormat(iso = DATE_TIME) Instant from,
        @DateTimeFormat(iso = DATE_TIME) Instant to,
        Granularity granularity,
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String skill,
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String city,
        @Min(0) Integer page,
        @Min(1) @Max(20) Integer size) {

    private static final Instant DEFAULT_FROM = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant DEFAULT_TO = Instant.parse("9999-01-01T00:00:00Z");

    public AnalyticsFilter {
        from = requireNonNullElse(from, DEFAULT_FROM);
        to = requireNonNullElse(to, DEFAULT_TO);
        granularity = requireNonNullElse(granularity, Granularity.DAY);
        page = requireNonNullElse(page, 0);
        size = requireNonNullElse(size, 10);
    }

    @AssertTrue(message = "from must be before to")
    public boolean isDateRangeValid() {
        return from.isBefore(to);
    }

    public long offset() {
        return (long) page * size;
    }
}
