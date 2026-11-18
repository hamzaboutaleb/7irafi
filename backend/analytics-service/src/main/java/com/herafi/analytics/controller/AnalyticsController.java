package com.herafi.analytics.controller;

import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsFilter;
import com.herafi.analytics.dto.AnalyticsResponse;
import com.herafi.analytics.dto.ApplicationStatus;
import com.herafi.analytics.dto.JobStatus;
import com.herafi.analytics.repository.ApplicationAnalyticsRepository;
import com.herafi.analytics.repository.JobAnalyticsRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics/my")
public class AnalyticsController {
    private final JobAnalyticsRepository jobs;
    private final ApplicationAnalyticsRepository applications;

    public AnalyticsController(JobAnalyticsRepository jobs, ApplicationAnalyticsRepository applications) {
        this.jobs = jobs;
        this.applications = applications;
    }

    @GetMapping("/jobs")
    public AnalyticsResponse jobs(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @ModelAttribute AnalyticsFilter filter,
            @RequestParam(required = false) JobStatus status) {
        return jobs.summarize(userId, filter, status);
    }

    @GetMapping("/applications")
    public AnalyticsResponse applications(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @ModelAttribute AnalyticsFilter filter,
            @RequestParam(required = false) ApplicationStatus status) {
        return applications.summarize(userId, filter, status);
    }
}
