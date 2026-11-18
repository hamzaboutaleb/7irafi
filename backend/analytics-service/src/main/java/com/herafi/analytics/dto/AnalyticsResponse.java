package com.herafi.analytics.dto;

import java.util.List;

public record AnalyticsResponse(long total, List<Count> statusDistribution, List<Count> activity) {

    public record Count(String value, long count) { }
}
