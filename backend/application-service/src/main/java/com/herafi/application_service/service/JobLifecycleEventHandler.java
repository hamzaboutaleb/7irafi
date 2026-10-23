package com.herafi.application_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.entity.ProcessedJobEvent;
import com.herafi.application_service.event.ApplicationAnalyticsEvent;
import com.herafi.application_service.event.JobLifecycleEvent;
import com.herafi.application_service.repository.JobApplicationRepository;
import com.herafi.application_service.repository.JobLifecycleStateRepository;
import com.herafi.application_service.repository.ProcessedJobEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobLifecycleEventHandler {

    private final JobLifecycleStateRepository jobStates;
    private final ProcessedJobEventRepository processed;
    private final JobApplicationRepository applications;
    private final OutboxEventWriter writer;

    @Transactional
    public void handle(JobLifecycleEvent event) {
        var jobId = event.data().jobId();
        jobStates.ensureExists(jobId);
        var state = jobStates.lockByJobId(jobId);
        if (processed.existsById(event.eventId())) {
            return;
        }
        if (event.jobVersion() > state.getJobVersion()) {
            state.apply(event);
            // Deletion has no additional application transition in the analytics contract.
            if (event.eventType() != JobLifecycleEvent.Type.JOB_DELETED) {
                for (JobApplication application : applications.findAllByJobId(jobId)) {
                    if (application.getJobSkill() == null || application.getJobCity() == null) {
                        application.setJobSkill(event.data().skill());
                        application.setJobCity(event.data().city());
                    }
                    var outcome = outcome(event, application);
                    if (outcome != application.getAnalyticsStatus()) {
                        application.setAnalyticsStatus(outcome);
                        var type = outcome == ApplicationAnalyticsEvent.Status.ACCEPTED
                                ? ApplicationAnalyticsEvent.Type.APPLICATION_ACCEPTED
                                : ApplicationAnalyticsEvent.Type.APPLICATION_REJECTED;
                        // An HTTP summary may precede a concurrent assignment; submission still comes first.
                        var occurredAt = event.timestamp().isBefore(application.getCreatedAt())
                                ? application.getCreatedAt() : event.timestamp();
                        writer.recordApplicationEvent(application, type, occurredAt);
                    }
                }
            }
        }
        processed.save(new ProcessedJobEvent(event.eventId(), jobId));
    }

    private ApplicationAnalyticsEvent.Status outcome(JobLifecycleEvent event, JobApplication application) {
        // Assignment and cancellation resolve pending applications. Completion preserves resolved outcomes.
        if (application.getAnalyticsStatus() != ApplicationAnalyticsEvent.Status.PENDING) {
            return application.getAnalyticsStatus();
        }
        return switch (event.data().status()) {
            case OPEN -> ApplicationAnalyticsEvent.Status.PENDING;
            case CANCELLED -> ApplicationAnalyticsEvent.Status.REJECTED;
            case ASSIGNED, COMPLETED -> application.getApplicantId().equals(event.assignedWorkerId())
                    ? ApplicationAnalyticsEvent.Status.ACCEPTED : ApplicationAnalyticsEvent.Status.REJECTED;
        };
    }
}
