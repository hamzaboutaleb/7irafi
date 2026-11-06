package com.herafi.rating_service.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.rating_service.client.JobClient.JobSummary;
import com.herafi.rating_service.client.JobClient;
import com.herafi.rating_service.client.UserClient;
import com.herafi.rating_service.dto.CreateRatingRequest;
import com.herafi.rating_service.dto.PageResponse;
import com.herafi.rating_service.dto.RatingResponse;
import com.herafi.rating_service.dto.UserRatingSummary;
import com.herafi.rating_service.entity.Rating;
import com.herafi.rating_service.exception.DuplicateRatingException;
import com.herafi.rating_service.exception.JobNotCompletedException;
import com.herafi.rating_service.exception.JobServiceUnavailableException;
import com.herafi.rating_service.exception.RatingException;
import com.herafi.rating_service.repository.RatingRepository.ScoreCount;
import com.herafi.rating_service.repository.RatingRepository;

import lombok.RequiredArgsConstructor;

/**
 * Checks the rules for creating ratings and reads ratings from the database.
 * The HTTP clients fetch information from other services; rating decisions stay here.
 *
 * The transaction annotations on the public methods tell Spring how to manage
 * their database work when another Spring bean calls this service:
 *
 * - @Transactional uses readOnly = false by default, so reads and writes are allowed.
 *   It starts a transaction, or joins one already running. Changes are committed
 *   when that transaction finishes successfully. By default, a RuntimeException
 *   or Error causes rollback; a checked exception does not automatically do so.
 * - @Transactional(readOnly = true) declares that a method only reads data. Spring
 *   and the database tools can use this hint to avoid unnecessary write-related
 *   work. It is not a security rule or a guarantee that every write will be blocked.
 *
 * Joining an existing transaction normally means using its settings. Calling a
 * method from inside this same object does not start a new annotated transaction.
 * Transactions here cover local database work, not HTTP calls to other services:
 * rolling back cannot undo an action performed by another service.
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository repository;
    private final JobClient jobClient;
    private final UserClient userClient;

    /**
     * Validates the job and both participants before saving their rating.
     * Flow: reject duplicates -> fetch the job -> check completion and participants
     * -> confirm the recipient exists -> build and save -> return the response.
     * The rater ID comes from the caller's authenticated identity, while the
     * recipient ID comes from the request and must match the other job participant.
     *
     * The early duplicate check avoids unnecessary calls to other services, but
     * the database's unique index is still needed if two requests arrive together.
     */
    @Transactional
    public RatingResponse create(UUID raterId, CreateRatingRequest request) {
        ensureNotAlreadyRated(raterId, request);

        JobSummary job = jobClient.getSummary(request.jobId());
        validateCompletedJob(job);
        validateParticipants(job, raterId, request.rateeId());
        userClient.validateUserExists(request.rateeId());

        Rating rating = buildRating(raterId, request);
        Rating saved = repository.save(rating);
        Map<UUID, String> reviewerNames = userClient.getDisplayNames(Set.of(raterId));
        return toResponse(saved, reviewerNames);
    }

    /**
     * Finds the ratings for one job, newest first, and converts them to API responses.
     * ID breaks ties when ratings have the same creation time. If there are no
     * ratings, the result is an empty list rather than an error.
     */
    @Transactional(readOnly = true)
    public List<RatingResponse> getByJobId(UUID jobId) {
        List<Rating> ratings = repository.findByJobIdOrderByCreatedAtDescIdDesc(jobId);
        Map<UUID, String> reviewerNames = getReviewerNames(ratings);
        return ratings.stream().map(rating -> toResponse(rating, reviewerNames)).toList();
    }

    /**
     * Finds one page of ratings received by this user: the user is the ratee,
     * meaning the person being reviewed. Converts the ratings to API responses
     * and keeps the page information, such as the total number of ratings.
     */
    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> getReceivedByUserId(UUID userId, Pageable pageable) {
        Page<Rating> ratings = repository.findByRateeIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return toPageResponse(ratings);
    }

    /**
     * Finds one page of ratings written by this user: the user is the rater.
     * Uses the same response format as received ratings, but filters by the
     * author instead of the person being reviewed.
     */
    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> getGivenByUserId(UUID userId, Pageable pageable) {
        Page<Rating> ratings = repository.findByRaterIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return toPageResponse(ratings);
    }

    /**
     * Builds a summary of ratings received by this user.
     * First gives every star level a zero count, then fills in the database's
     * grouped counts and calculates the average. The database returns counts for
     * each score, so we do not need to load every individual rating.
     * A user with no ratings receives zero counts and an average of 0.0.
     */
    @Transactional(readOnly = true)
    public UserRatingSummary getSummary(UUID userId) {
        List<ScoreCount> scoreCounts = repository.countScoresByRateeId(userId);
        return calculateSummary(userId, scoreCounts);
    }

    /**
     * Rejects an existing rating before making HTTP calls to other services.
     * The database's unique index still protects against simultaneous requests
     * that both pass this check before either one saves.
     */
    private void ensureNotAlreadyRated(UUID raterId, CreateRatingRequest request) {
        if (repository.existsByJobIdAndRaterIdAndRateeId(request.jobId(), raterId, request.rateeId())) {
            throw new DuplicateRatingException();
        }
    }

    /**
     * Allows ratings only after the work is marked completed.
     * A completed job without an assigned worker is inconsistent information:
     * we cannot confirm who should be rated, so we stop instead of guessing.
     * The unavailable error here means validation cannot be completed reliably;
     * it does not necessarily mean the job service is offline.
     */
    private void validateCompletedJob(JobSummary job) {
        if (!"COMPLETED".equals(job.status())) {
            throw new JobNotCompletedException();
        }
        if (job.assignedWorkerId() == null) {
            throw new JobServiceUnavailableException();
        }
    }

    /**
     * Allows exactly two directions: client rates worker, or worker rates client.
     * A caller cannot rate an unrelated person, and someone outside the job cannot
     * submit a rating. The explicit self-rating check also rejects inconsistent
     * job data where the client and worker happen to have the same ID.
     * Called after validateCompletedJob, so the assigned worker ID is available.
     */
    private void validateParticipants(JobSummary job, UUID raterId, UUID rateeId) {
        boolean clientRatesWorker = raterId.equals(job.clientId()) && rateeId.equals(job.assignedWorkerId());
        boolean workerRatesClient = raterId.equals(job.assignedWorkerId()) && rateeId.equals(job.clientId());

        if ((!clientRatesWorker && !workerRatesClient) || raterId.equals(rateeId)) {
            throw new RatingException("INVALID_RATING_PARTICIPANT",
                    "Only the client and assigned professional can rate each other");
        }
    }

    /**
     * Builds the entity after the caller has passed the rating checks.
     * The builder only fills fields; it does not validate or save the rating.
     * The request's Integer score becomes a Short to match the entity field.
     * Rating's persistence callback supplies the ID and timestamp before insertion.
     */
    private Rating buildRating(UUID raterId, CreateRatingRequest request) {
        return Rating.builder()
                .jobId(request.jobId())
                .raterId(raterId)
                .rateeId(request.rateeId())
                .score(request.score().shortValue())
                .comment(request.comment())
                .build();
    }

    /**
     * Calculates the totals, average, and star counts from the supplied data.
     * This method does not call the database or any other service. Missing star
     * levels keep a zero count, and an empty list produces a zero average.
     */
    private UserRatingSummary calculateSummary(UUID userId, List<ScoreCount> scoreCounts) {
        // Include every star level, even when nobody has given that score yet.
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int score = 1; score <= 5; score++) {
            distribution.put(score, 0L);
        }
        long totalRatings = 0;
        long totalScore = 0;
        for (ScoreCount bucket : scoreCounts) {
            int score = bucket.getScore().intValue();
            distribution.put(score, bucket.getCount());
            totalRatings += bucket.getCount();
            // Three 4-star ratings contribute 12 to the total score.
            totalScore += score * bucket.getCount();
        }
        // Avoid division by zero. The double conversion keeps fractional averages,
        // for example 4.5, instead of discarding the decimal part.
        double averageScore = totalRatings == 0 ? 0.0 : (double) totalScore / totalRatings;
        return new UserRatingSummary(userId, averageScore, totalRatings, distribution);
    }

    /**
     * Converts a database entity into the response returned by the API.
     * Names are fetched together before mapping, rather than making an HTTP call
     * for every rating. If a profile is missing or the name lookup failed, use an
     * honest "Unknown user" label instead of inventing a name from an ID.
     */
    private RatingResponse toResponse(Rating rating, Map<UUID, String> reviewerNames) {
        return RatingResponse.from(rating, reviewerNames.getOrDefault(rating.getRaterId(), "Unknown user"));
    }

    /** Converts one page after loading its reviewer names in a single batch. */
    private PageResponse<RatingResponse> toPageResponse(Page<Rating> ratings) {
        Map<UUID, String> reviewerNames = getReviewerNames(ratings.getContent());
        return PageResponse.from(ratings.map(rating -> toResponse(rating, reviewerNames)));
    }

    /** A reviewer may appear several times on a page, so request each name only once. */
    private Map<UUID, String> getReviewerNames(List<Rating> ratings) {
        if (ratings.isEmpty()) {
            return Map.of();
        }
        Set<UUID> reviewerIds = ratings.stream().map(Rating::getRaterId).collect(Collectors.toSet());
        return userClient.getDisplayNames(reviewerIds);
    }
}
