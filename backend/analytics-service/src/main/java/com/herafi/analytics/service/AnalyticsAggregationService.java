package com.herafi.analytics.service;

import com.herafi.analytics.dto.TimeBucketResponse;
import com.herafi.analytics.repository.AnalyticsApplicationRepository;
import com.herafi.analytics.repository.AnalyticsJobRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsAggregationService {

    private final AnalyticsJobRepository jobRepository;
    private final AnalyticsApplicationRepository applicationRepository;

    public AnalyticsAggregationService(AnalyticsJobRepository jobRepository, AnalyticsApplicationRepository applicationRepository) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
    }

    public List<TimeBucketResponse> getJobActivity(UUID userId, Instant from, Instant to, String status, String skill, String city, String granularity) {
        List<Object[]> rawBuckets = jobRepository.getActivityBuckets(userId, from, to, status, skill, city, granularity);
        return mapToResponse(rawBuckets);
    }

    public List<TimeBucketResponse> getApplicationActivity(UUID userId, Instant from, Instant to, String status, String skill, String city, String granularity) {
        List<Object[]> rawBuckets = applicationRepository.getActivityBuckets(userId, from, to, status, skill, city, granularity);
        return mapToResponse(rawBuckets);
    }

    private List<TimeBucketResponse> mapToResponse(List<Object[]> rawBuckets) {
        List<TimeBucketResponse> response = new ArrayList<>();
        for (Object[] row : rawBuckets) {
            String period = (String) row[0];
            long count = ((Number) row[1]).longValue();
            response.add(new TimeBucketResponse(period, count));
        }
        return response;
    }
}
