package com.herafi.rating_service.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.rating_service.dto.ApiResponse;
import com.herafi.rating_service.dto.CreateRatingRequest;
import com.herafi.rating_service.dto.PageResponse;
import com.herafi.rating_service.dto.RatingResponse;
import com.herafi.rating_service.dto.UserRatingSummary;
import com.herafi.rating_service.service.RatingService;

@RestController
@RequestMapping("/ratings")
public class RatingController {

    private final RatingService service;

    public RatingController(RatingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RatingResponse>> create(
            @Valid @RequestBody CreateRatingRequest request,
            @RequestHeader("X-User-Id") UUID raterId) {
        RatingResponse response = service.create(raterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/job/{jobId}")
    public ApiResponse<List<RatingResponse>> getByJobId(@PathVariable UUID jobId) {
        return ApiResponse.success(service.getByJobId(jobId));
    }

    @GetMapping("/user/{userId}/received")
    public ApiResponse<PageResponse<RatingResponse>> getReceivedByUserId(@PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getReceivedByUserId(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/user/{userId}/given")
    public ApiResponse<PageResponse<RatingResponse>> getGivenByUserId(@PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(service.getGivenByUserId(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/user/{userId}/summary")
    public ApiResponse<UserRatingSummary> getSummary(@PathVariable UUID userId) {
        return ApiResponse.success(service.getSummary(userId));
    }
}
