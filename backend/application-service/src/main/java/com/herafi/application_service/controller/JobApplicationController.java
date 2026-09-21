package com.herafi.application_service.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.herafi.application_service.dto.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.application_service.dto.ApplicationResponse;
import com.herafi.application_service.dto.CreateApplicationRequest;
import com.herafi.application_service.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/applications")
@RequiredArgsConstructor
public class JobApplicationController {
    private final JobApplicationService service;

    @GetMapping("/my")
    public PageResponse<ApplicationResponse> getMyApplications(
            @RequestHeader("X-User-Id") UUID applicantId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return service.getMyApplications(applicantId, page, size);
    }

    @GetMapping("/job/{jobId}")
    public PageResponse<ApplicationResponse> getJobApplications(
            @PathVariable UUID jobId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size) {
        return service.getJobApplications(jobId, userId, page, size);
    }

    @PatchMapping("/{applicationId}/accept")
    public ApplicationResponse accept(@PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") UUID userId) {
        return service.accept(applicationId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse apply(@RequestHeader("X-User-Id") UUID applicantId,
            @Valid @RequestBody CreateApplicationRequest request) {
        return service.apply(request.jobId(), applicantId);
    }
}
