package com.herafi.rating_service.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.rating_service.dto.ApiResponse;
import com.herafi.rating_service.dto.CreateRatingRequest;
import com.herafi.rating_service.dto.PageResponse;
import com.herafi.rating_service.dto.RatingResponse;
import com.herafi.rating_service.dto.UserRatingSummary;
import com.herafi.rating_service.service.RatingService;

import lombok.RequiredArgsConstructor;

/**
 * Receives rating requests and returns JSON responses.
 * Spring reads and validates the input; RatingService checks the rating rules
 * and reads or saves the data.
 */
@RestController
@RequestMapping("/ratings")
@RequiredArgsConstructor
public class RatingController {

    // Lombok creates the constructor, and Spring supplies this service.
    private final RatingService service;

    /**
     * Creates a rating and returns 201 Created with the saved rating.
     * The status is set by @ResponseStatus, so this method returns only the body.
     * The gateway sets X-User-Id after checking the user's token. We do not accept
     * the rater's ID in the request body, where a caller could invent an identity.
     * The trusted-caller filter checks the gateway key before this method runs.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RatingResponse> create(
            @Valid @RequestBody CreateRatingRequest request,
            @RequestHeader("X-User-Id") UUID raterId) {
        RatingResponse rating = service.create(raterId, request);
        return ApiResponse.success(rating);
    }

    /** Returns the ratings for one job, or an empty list if nobody has rated it. */
    @GetMapping("/job/{jobId}")
    public ApiResponse<List<RatingResponse>> getByJobId(@PathVariable UUID jobId) {
        return ApiResponse.success(service.getByJobId(jobId));
    }

    /**
     * Lists reviews written about this user, newest first.
     * Spring builds Pageable from the URL's page and size parameters. Page 0 is
     * the first page; the default size is 20. application.properties caps size
     * at 100, so a larger request is reduced to 100 rather than rejected.
     */
    @GetMapping("/user/{userId}/received")
    public ApiResponse<PageResponse<RatingResponse>> getReceivedByUserId(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(service.getReceivedByUserId(userId, paginationOnly(pageable)));
    }

    /**
     * Lists reviews written by this user, using the same paging rules as received
     * reviews.
     */
    @GetMapping("/user/{userId}/given")
    public ApiResponse<PageResponse<RatingResponse>> getGivenByUserId(@PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(service.getGivenByUserId(userId, paginationOnly(pageable)));
    }

    /**
     * Returns the user's average score, review count, and count for each star
     * level.
     */
    @GetMapping("/user/{userId}/summary")
    public ApiResponse<UserRatingSummary> getSummary(@PathVariable UUID userId) {
        return ApiResponse.success(service.getSummary(userId));
    }

    /**
     * Keeps the requested page and size, but ignores any sort parameter.
     * The repository already orders reviews newest first, with ID as a tie-breaker.
     * Passing arbitrary sort fields through could cause database query errors.
     */
    private Pageable paginationOnly(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
