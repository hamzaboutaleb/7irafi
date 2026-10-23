package com.herafi.application_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.entity.OutboxEvent;
import com.herafi.application_service.event.ApplicationAnalyticsEvent;
import com.herafi.application_service.repository.JobApplicationRepository;
import com.herafi.application_service.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxEventWriter {

    public static final String ANALYTICS_DESTINATION = "analytics.events";

    private final JobApplicationRepository applications;
    private final OutboxEventRepository events;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordApplicationEvent(JobApplication application, ApplicationAnalyticsEvent.Type type,
            Instant occurredAt) {
        applications.flush();
        var data = new ApplicationAnalyticsEvent.Data(application.getId(), application.getJobId(),
                application.getApplicantId(), application.getJobSkill(), application.getJobCity(),
                application.getAnalyticsStatus(), application.getCreatedAt());
        var event = new ApplicationAnalyticsEvent(UUID.randomUUID(), type, occurredAt, data);
        events.save(new OutboxEvent(event.eventId(), type, ANALYTICS_DESTINATION,
                application.getId(), application.getVersion(), mapper.writeValueAsString(event), occurredAt));
    }
}
