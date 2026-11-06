package com.herafi.rating_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.rating_service.entity.Rating;

/** Names and the job title can be absent; clients should show a localised fallback. */
public record RatingResponse(
        UUID id,
        UUID jobId,
        UUID raterId,
        UUID rateeId,
        Integer score,
        String comment,
        Instant createdAt,
        String raterName,
        String rateeName,
        String jobTitle) {

    public static RatingResponse from(Rating rating, String raterName, String rateeName, String jobTitle) {
        return new RatingResponse(
                rating.getId(),
                rating.getJobId(),
                rating.getRaterId(),
                rating.getRateeId(),
                rating.getScore() != null ? rating.getScore().intValue() : null,
                rating.getComment(),
                rating.getCreatedAt(),
                raterName, rateeName, jobTitle);
    }
}
