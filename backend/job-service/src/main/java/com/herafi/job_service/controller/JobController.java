package com.herafi.job_service.controller;

import java.util.UUID;
import java.util.List;

import org.apache.tomcat.util.net.openssl.ciphers.Authentication;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.herafi.job_service.dto.CreateJobRequest;
import com.herafi.job_service.dto.JobResponse;
import com.herafi.job_service.dto.UpdateJobRequest;
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
    public List<JobResponse> getJobs() {
        return jobService.getJobs()
                .stream()
                .map(JobResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(
            @PathVariable UUID id) {
        Job job = jobService.getJob(id);

        return ResponseEntity.ok(JobResponse.from(job));
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
            @PathVariable UUID id) {

        Job job = jobService.complete(id);

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
}
