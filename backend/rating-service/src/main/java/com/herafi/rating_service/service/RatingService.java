package com.herafi.rating_service.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.rating_service.client.JobClient;
import com.herafi.rating_service.client.JobClient.JobSummary;
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
import com.herafi.rating_service.repository.RatingRepository;

@Service
public class RatingService {

    private final RatingRepository repository;
    private final JobClient jobClient;
    private final UserClient userClient;

    public RatingService(RatingRepository repository, JobClient jobClient, UserClient userClient) {
        this.repository = repository;
        this.jobClient = jobClient;
        this.userClient = userClient;
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> getByJobId(UUID jobId) {
        return repository.findByJobIdOrderByCreatedAtDescIdDesc(jobId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> getReceivedByUserId(UUID userId, Pageable pageable) {
        return PageResponse.from(repository.findByRateeIdOrderByCreatedAtDescIdDesc(userId, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> getGivenByUserId(UUID userId, Pageable pageable) {
        return PageResponse.from(repository.findByRaterIdOrderByCreatedAtDescIdDesc(userId, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UserRatingSummary getSummary(UUID userId) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int score = 1; score <= 5; score++) {
            distribution.put(score, 0L);
        }
        long total = 0;
        long weightedScore = 0;
        for (RatingRepository.ScoreCount bucket : repository.countScoresByRateeId(userId)) {
            int score = bucket.getScore().intValue();
            distribution.put(score, bucket.getCount());
            total += bucket.getCount();
            weightedScore += score * bucket.getCount();
        }
        return new UserRatingSummary(userId, total == 0 ? 0 : (double) weightedScore / total,
                total, distribution);
    }

    private RatingResponse toResponse(Rating rating) {
        return RatingResponse.from(rating, "User " + rating.getRaterId().toString().substring(0, 8));
    }

    @Transactional
    public RatingResponse create(UUID raterId, CreateRatingRequest request) {
        if (repository.existsByJobIdAndRaterIdAndRateeId(request.jobId(), raterId, request.rateeId())) {
            throw new DuplicateRatingException();
        }

        JobSummary job = jobClient.getSummary(request.jobId());
        validateCompletedJob(job);
        validateParticipants(job, raterId, request.rateeId());
        userClient.validateUserExists(request.rateeId());

        Rating rating = new Rating();
        rating.setId(UUID.randomUUID());
        rating.setJobId(request.jobId());
        rating.setRaterId(raterId);
        rating.setRateeId(request.rateeId());
        rating.setScore(request.score().shortValue());
        rating.setComment(request.comment());

        Rating saved = repository.save(rating);

        return RatingResponse.from(saved, "User " + raterId.toString().substring(0, 8));
    }

    private void validateCompletedJob(JobSummary job) {
        if (!"COMPLETED".equals(job.status())) {
            throw new JobNotCompletedException();
        }
        if (job.assignedWorkerId() == null) {
            throw new JobServiceUnavailableException();
        }
    }

    private void validateParticipants(JobSummary job, UUID raterId, UUID rateeId) {
        boolean clientRatesWorker = raterId.equals(job.clientId()) && rateeId.equals(job.assignedWorkerId());
        boolean workerRatesClient = raterId.equals(job.assignedWorkerId()) && rateeId.equals(job.clientId());

        if ((!clientRatesWorker && !workerRatesClient) || raterId.equals(rateeId)) {
            throw new RatingException("INVALID_RATING_PARTICIPANT",
                    "Only the client and assigned professional can rate each other");
        }
    }
}
