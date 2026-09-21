package com.herafi.job_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.job_service.dto.AssignWorkerRequest;
import com.herafi.job_service.dto.JobSummaryResponse;
import com.herafi.job_service.service.JobService;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/jobs")
@RequiredArgsConstructor
public class InternalJobController {

    private final JobService jobService;

    @GetMapping("/{id}/summary")
    public JobSummaryResponse getSummary(@PathVariable UUID id) {
        return jobService.getSummary(id);
    }

    @PatchMapping("/{id}/assign")
    public JobSummaryResponse assignWorker(
            @PathVariable UUID id,
            @Valid @RequestBody AssignWorkerRequest request) {

                // System.out.println("request is " + (request == null ? "null" : request));
        return jobService.assignWorker(id, request.workerId());
    }

}
