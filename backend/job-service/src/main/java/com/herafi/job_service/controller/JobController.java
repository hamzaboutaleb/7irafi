package com.herafi.job_service.controller;

import java.util.UUID;
import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.JobFilter;
import com.herafi.job_service.dto.JobImageResponse;
import com.herafi.job_service.dto.JobListResponse;
import com.herafi.job_service.dto.JobResponse;
import com.herafi.job_service.dto.PageResponse;
import com.herafi.job_service.dto.UpdateJobRequest;
import com.herafi.job_service.entity.Job;
import com.herafi.job_service.exceptions.InvalidImageException;
import com.herafi.job_service.mapper.JobMapper;
import com.herafi.job_service.service.JobService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobMapper jobMapper;

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @RequestHeader("X-User-Id") UUID clientId,
            @Valid @RequestBody CreateJobRequest request) {

        Job job = jobService.create(clientId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JobResponse.from(job));
    }

    @GetMapping
    public PageResponse<JobListResponse> getJobs(
            JobFilter filter,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size) {

        return jobService.getJobs(filter, sort, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        Job job = jobService.getJob(id);

        return ResponseEntity.ok(jobMapper.toDetailResponse(job, userId));
    }

    @GetMapping("/my")
    public List<JobResponse> getMyJobs(
            @RequestHeader("X-User-Id") UUID clientId) {

        return jobService.getMyJobs(clientId)
                .stream()
                .map(JobResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponse> updateJob(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID clientId,
            @Valid @RequestBody UpdateJobRequest request) {

        Job job = jobService.update(id, clientId, request);

        return ResponseEntity.ok(JobResponse.from(job));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<JobResponse> completeJob(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        Job job = jobService.complete(id, userId);

        return ResponseEntity.ok(JobResponse.from(job));
    }

    @GetMapping("/skills")
    public List<String> getAllSkills() {
        return jobService.getAllSkills();
    }

    @GetMapping("/cities")
    public List<String> getAllCities() {
        return jobService.getAllCities();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {

        boolean isAdmin = "ADMIN".equals(role);

        jobService.deleteJob(id, userId, isAdmin);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<JobResponse> cancelJob(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {

        boolean isAdmin = "ADMIN".equals(role);
        Job job = jobService.cancelJob(id, userId, isAdmin);

        return ResponseEntity.ok(JobResponse.from(job));
    }

    @PostMapping(value = "/{jobId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobImageResponse> uploadImage(
            @PathVariable UUID jobId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") UUID userId) {
        try {

            JobImageResponse response = jobService.uploadImage(jobId, userId, file);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (IOException e) {
            throw new InvalidImageException("Failed to upload image");
        }
    }

    @DeleteMapping("/{jobId}/images/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID jobId,
            @PathVariable UUID imageId,
            @RequestHeader("X-User-Id") UUID userId) {

        jobService.deleteImage(jobId, imageId, userId);

        return ResponseEntity.noContent().build();
    }
}
