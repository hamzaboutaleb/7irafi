package com.herafi.analytics.controller;

import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsFilter;
import com.herafi.analytics.dto.AnalyticsResponse;
import com.herafi.analytics.dto.AnalyticsResponse.ApplicationRecord;
import com.herafi.analytics.dto.AnalyticsResponse.JobRecord;
import com.herafi.analytics.dto.ApplicationStatus;
import com.herafi.analytics.dto.JobStatus;
import com.herafi.analytics.service.AnalyticsQueries;
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
    private final AnalyticsQueries queries;

    public AnalyticsController(AnalyticsQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/jobs")
    public AnalyticsResponse<JobRecord> jobs(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @ModelAttribute AnalyticsFilter filter,
            @RequestParam(required = false) JobStatus status) {
        return queries.jobs(userId, filter, status);
    }

    @GetMapping("/applications")
    public AnalyticsResponse<ApplicationRecord> applications(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @ModelAttribute AnalyticsFilter filter,
            @RequestParam(required = false) ApplicationStatus status) {
        return queries.applications(userId, filter, status);
    }
}
