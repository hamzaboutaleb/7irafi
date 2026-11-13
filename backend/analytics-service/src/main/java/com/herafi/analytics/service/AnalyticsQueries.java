package com.herafi.analytics.service;

import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsFilter;
import com.herafi.analytics.dto.AnalyticsResponse;
import com.herafi.analytics.dto.AnalyticsResponse.ApplicationRecord;
import com.herafi.analytics.dto.AnalyticsResponse.JobRecord;
import com.herafi.analytics.dto.ApplicationStatus;
import com.herafi.analytics.dto.JobStatus;
import com.herafi.analytics.repository.ApplicationAnalyticsRepository;
import com.herafi.analytics.repository.JobAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnalyticsQueries {
    private final JobAnalyticsRepository jobs;
    private final ApplicationAnalyticsRepository applications;

    public AnalyticsQueries(JobAnalyticsRepository jobs, ApplicationAnalyticsRepository applications) {
        this.jobs = jobs;
        this.applications = applications;
    }

    public AnalyticsResponse<JobRecord> jobs(UUID owner, AnalyticsFilter filter, JobStatus status) {
        return jobs.summarize(owner, filter, status);
    }

    public AnalyticsResponse<ApplicationRecord> applications(UUID owner, AnalyticsFilter filter, ApplicationStatus status) {
        return applications.summarize(owner, filter, status);
    }
}
