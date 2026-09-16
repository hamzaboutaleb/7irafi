package com.herafi.user_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.service.ProfessionalProfileService;

@RestController
public class InternalWorkerController {
    private final ProfessionalProfileService service;

    public InternalWorkerController(ProfessionalProfileService service) {
        this.service = service;
    }

    @GetMapping("/internal/users/{userId}/worker-eligibility")
    public WorkerEligibility eligibility(@PathVariable UUID userId) {
        return new WorkerEligibility(userId, service.isActiveWorker(userId));
    }

    public record WorkerEligibility(UUID userId, boolean eligible) {
    }
}
