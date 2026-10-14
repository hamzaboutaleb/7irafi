package com.herafi.rating_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.rating_service.entity.Rating;

public record RatingResponse(
        UUID id,
        UUID jobId,
        UUID raterId,
        UUID rateeId,
        Integer score,
        String comment,
        Instant createdAt,
        String raterName) {

    public static RatingResponse from(Rating rating, String raterName) {
        return new RatingResponse(
                rating.getId(),
                rating.getJobId(),
                rating.getRaterId(),
                rating.getRateeId(),
                rating.getScore() != null ? rating.getScore().intValue() : null,
                rating.getComment(),
                rating.getCreatedAt(),
                raterName);
    }
}