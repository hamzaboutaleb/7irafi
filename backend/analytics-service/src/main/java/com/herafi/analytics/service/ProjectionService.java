package com.herafi.analytics.service;

import java.time.Instant;
import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.repository.ProjectionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionService {
    private final ProjectionRepository repository;
    private final ApplicationEventPublisher publisher;

    public ProjectionService(ProjectionRepository repository, ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional
    public void apply(AnalyticsEvent event) {
        // The event receipt and projection write commit together, before Kafka acknowledges.
        if (!repository.recordEvent(event.eventId())) {
            return;
        }

        Invalidation invalidation = switch (event) {
            case JobEvent job -> projectJob(job);
            case ApplicationEvent application -> projectApplication(application);
        };
        publisher.publishEvent(invalidation);
    }

    private Invalidation projectJob(JobEvent event) {
        switch (event.eventType()) {
            case JOB_CREATED, JOB_STATUS_CHANGED -> repository.saveJob(event.data());
            case JOB_DELETED -> repository.deleteJob(event.data().jobId());
        }
        return new Invalidation(event.data().clientId(), event.eventId(), "jobs", event.timestamp());
    }

    private Invalidation projectApplication(ApplicationEvent event) {
        repository.saveApplication(event.data());
        return new Invalidation(event.data().applicantId(), event.eventId(), "applications", event.timestamp());
    }

    public record Invalidation(UUID userId, UUID eventId, String resource, Instant occurredAt) { }
}
