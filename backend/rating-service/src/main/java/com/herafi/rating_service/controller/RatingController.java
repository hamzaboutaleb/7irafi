package com.herafi.rating_service.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.rating_service.dto.ApiResponse;
import com.herafi.rating_service.dto.CreateRatingRequest;
import com.herafi.rating_service.dto.RatingResponse;
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
    public String getByJobId(@PathVariable String jobId) {
        return "get ratings for job: " + jobId;
    }

    @GetMapping("/user/{userId}/received")
    public String getReceivedByUserId(@PathVariable String userId) {
        return "get received ratings for user: " + userId;
    }

    @GetMapping("/user/{userId}/given")
    public String getGivenByUserId(@PathVariable String userId) {
        return "get given ratings for user: " + userId;
    }

    @GetMapping("/user/{userId}/summary")
    public String getSummary(@PathVariable String userId) {
        return "get rating summary for user: " + userId;
    }
}