package com.herafi.job_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.JobResponse;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.service.JobService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/test")
    public String test() {
        return "ok";
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @RequestHeader("X-User-Id") UUID clientId,
            @Valid @RequestBody CreateJobRequest request) {
        Job job = jobService.create(clientId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JobResponse.from(job));
    }
}
