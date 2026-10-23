package com.herafi.analytics.listener;

import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.entity.AnalyticsApplication;
import com.herafi.analytics.entity.AnalyticsJob;
import com.herafi.analytics.repository.AnalyticsApplicationRepository;
import com.herafi.analytics.repository.AnalyticsJobRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalyticsEventListener {

    private final AnalyticsJobRepository jobRepository;
    private final AnalyticsApplicationRepository applicationRepository;

    public AnalyticsEventListener(AnalyticsJobRepository jobRepository, AnalyticsApplicationRepository applicationRepository) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
    }

    @KafkaListener(topics = "jobs.lifecycle", groupId = "analytics-service-group")
    @Transactional
    public void handleJobEvent(JobEvent event) {
        AnalyticsJob job = AnalyticsJob.builder()
                .jobId(event.getJobId())
                .clientId(event.getClientId())
                .status(event.getStatus())
                .skill(event.getSkill())
                .city(event.getCity())
                .createdAt(event.getTimestamp())
                .build();
        
        jobRepository.save(job);
    }

    @KafkaListener(topics = "analytics.events", groupId = "analytics-service-group")
    @Transactional
    public void handleApplicationEvent(ApplicationEvent event) {
        AnalyticsApplication app = AnalyticsApplication.builder()
                .applicationId(event.getApplicationId())
                .jobId(event.getJobId())
                .applicantId(event.getApplicantId())
                .jobSkill(event.getJobSkill())
                .jobCity(event.getJobCity())
                .status(event.getStatus())
                .submittedAt(event.getTimestamp())
                .build();

        applicationRepository.save(app);
    }
}