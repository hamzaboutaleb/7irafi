package com.herafi.analytics.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.herafi.analytics.dto.AnalyticsEvent;
import com.herafi.analytics.dto.ApplicationEvent;
import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.repository.ProjectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionService {
    private final ProjectionRepository repository;

    public ProjectionService(ProjectionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<Invalidation> apply(AnalyticsEvent event) {
        // The event receipt and projection write commit together, before Kafka acknowledges.
        if (!repository.recordEvent(event.eventId())) {
            return Optional.empty();
        }

        return Optional.of(switch (event) {
            case JobEvent job -> {
                if (job.eventType() == JobEvent.Type.JOB_DELETED) {
                    repository.deleteJob(job.data().jobId());
                } else {
                    repository.saveJob(job.data());
                }

                yield new Invalidation(job.data().clientId(), job.eventId(), "jobs", job.timestamp());
            }
            case ApplicationEvent application -> {
                repository.saveApplication(application.data());

                yield new Invalidation(
                        application.data().applicantId(), application.eventId(),
                        "applications", application.timestamp());
            }
        });
    }

    public record Invalidation(UUID userId, UUID eventId, String resource, Instant occurredAt) { }
}
