package com.herafi.job_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.OutboxEvent;
import com.herafi.job_service.event.JobAnalyticsEvent;
import com.herafi.job_service.event.JobLifecycleEvent;
import com.herafi.job_service.repository.JobRepository;
import com.herafi.job_service.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxEventWriter {

    public static final String ANALYTICS_DESTINATION = "analytics.events";
    public static final String LIFECYCLE_DESTINATION = "jobs.lifecycle";

    private final JobRepository jobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordJobEvent(Job job, JobAnalyticsEvent.Type type) {
        // Flush inside the caller's transaction to capture generated timestamps and the new @Version.
        // The job and both outbox inserts still roll back together if any subsequent write fails.
        jobRepository.flush();

        UUID eventId = UUID.randomUUID();
        var timestamp = job.getUpdatedAt();
        var data = new JobAnalyticsEvent.Data(job.getId(), job.getClientId(), job.getStatus(),
                job.getSkill(), job.getCity(), job.getCreatedAt());
        var analytics = new JobAnalyticsEvent(eventId, type, timestamp, data);
        var lifecycle = new JobLifecycleEvent(eventId, type, timestamp, data,
                job.getAssignedWorkerId(), job.getVersion());

        outboxEventRepository.saveAll(List.of(
                new OutboxEvent(eventId, type, ANALYTICS_DESTINATION, job.getId(), job.getVersion(),
                        objectMapper.writeValueAsString(analytics), timestamp),
                new OutboxEvent(eventId, type, LIFECYCLE_DESTINATION, job.getId(), job.getVersion(),
                        objectMapper.writeValueAsString(lifecycle), timestamp)));
    }
}
