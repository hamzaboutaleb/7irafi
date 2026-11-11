package com.herafi.rating_service.dto;

import java.util.Map;
import java.util.UUID;

public record UserRatingSummary(UUID userId, double averageScore, long totalRatings,
        Map<Integer, Long> scoreDistribution) {
}
