package com.herafi.rating_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.rating_service.client.JobClient;
import com.herafi.rating_service.client.UserClient;
import com.herafi.rating_service.dto.CreateRatingRequest;
import com.herafi.rating_service.dto.RatingResponse;
import com.herafi.rating_service.entity.Rating;
import com.herafi.rating_service.exception.DuplicateRatingException;
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

    @Transactional
    public RatingResponse create(UUID raterId, CreateRatingRequest request) {
        if (repository.existsByJobIdAndRaterIdAndRateeId(request.jobId(), raterId, request.rateeId())) {
            throw new DuplicateRatingException();
        }

        jobClient.validateJobCompleted(request.jobId(), raterId);
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
}