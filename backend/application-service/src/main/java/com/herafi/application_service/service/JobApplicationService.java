package com.herafi.application_service.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.herafi.application_service.client.JobClient;
import com.herafi.application_service.client.UserClient;
import com.herafi.application_service.dto.ApplicationResponse;
import com.herafi.application_service.dto.ChatContextResponse;
import com.herafi.application_service.dto.PageResponse;
import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.event.NotificationRequestedEvent;
import com.herafi.application_service.exceptions.ApplicationException;
import com.herafi.application_service.repository.JobApplicationRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobApplicationService {

    private final JobApplicationRepository repository;
    private final JobClient jobClient;
    private final UserClient userClient;
    private final NotificationPublisher notificationPublisher;
    private final MeterRegistry meterRegistry;
    private final ApplicationSubmissionService submissionService;

    public ApplicationResponse apply(UUID jobId, UUID applicantId) {
        userClient.requireActiveWorker(applicantId);
        var job = jobClient.getSummary(jobId);
        if (job.clientId().equals(applicantId)) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "OWN_JOB_APPLICATION",
                    "You cannot apply to your own job");
        }
        if (!"OPEN".equals(job.status())) {
            throw new ApplicationException(HttpStatus.CONFLICT, "JOB_NOT_OPEN",
                    "Applications are only allowed for open jobs");
        }
        JobApplication application = submissionService.submit(job, applicantId);
        var notification = new NotificationRequestedEvent(UUID.randomUUID(), job.clientId(),
                "APPLICATION_RECEIVED", "New application",
                "A professional applied to your job.", "/jobs/" + jobId + "/applicants");
        afterCommit(() -> {
            Counter.builder("herafi.applications.submitted")
                    .description("Job applications submitted")
                    .register(meterRegistry)
                    .increment();
            log.atInfo().addKeyValue("event", "application_submitted")
                    .log("Application submitted applicationId={}", application.getId());
            notificationPublisher.publish(notification);
        });

        return new ApplicationResponse(application.getId(), jobId, applicantId,
                application.getCreatedAt(), "PENDING");
    }

    public ChatContextResponse getChatContext(UUID applicationId) {
        var application = repository.findById(applicationId)
                .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND", "Application not found"));
        var job = jobClient.getSummary(application.getJobId());
        return new ChatContextResponse(
                application.getId(),
                application.getJobId(),
                application.getApplicantId(),
                job.clientId(),
                getStatus(job, application.getApplicantId()));
    }

    public ApplicationResponse accept(UUID applicationId, UUID userId) {
        var application = repository.findById(applicationId)
                .orElseThrow(() -> new ApplicationException(HttpStatus.NOT_FOUND,
                        "APPLICATION_NOT_FOUND", "Application not found"));
        var job = jobClient.getSummary(application.getJobId());
        if (!job.clientId().equals(userId)) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "JOB_ACCESS_DENIED",
                    "Only the job owner can accept an applicant");
        }
        boolean alreadyAccepted = ("ASSIGNED".equals(job.status()) || "COMPLETED".equals(job.status()))
                && application.getApplicantId().equals(job.assignedWorkerId());
        if (!alreadyAccepted) {
            if (!"OPEN".equals(job.status())) {
                throw new ApplicationException(HttpStatus.CONFLICT, "JOB_NOT_OPEN",
                        "Only open jobs can accept an applicant");
            }
            job = jobClient.assignWorker(application.getJobId(), application.getApplicantId());

            Counter.builder("herafi.applications.accepted")
                    .description("Job applications accepted")
                    .register(meterRegistry)
                    .increment();
            log.atInfo().addKeyValue("event", "application_accepted")
                    .log("Application accepted applicationId={}", applicationId);

            notificationPublisher.publish(new NotificationRequestedEvent(
                    UUID.randomUUID(), application.getApplicantId(),
                    "APPLICATION_ACCEPTED",
                    "You were accepted",
                    "A client accepted your application. You can now agree the details.",
                    "/applications"));
        }
        return new ApplicationResponse(application.getId(), application.getJobId(),
                application.getApplicantId(), application.getCreatedAt(),
                getStatus(job, application.getApplicantId()));
    }

    public PageResponse<ApplicationResponse> getMyApplications(UUID applicantId, int page, int size) {
        var applications = repository.findByApplicantId(applicantId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<UUID, String> statuses = new HashMap<>();
        return PageResponse.from(applications, application -> new ApplicationResponse(
                application.getId(), application.getJobId(), application.getApplicantId(),
                application.getCreatedAt(), statuses.computeIfAbsent(application.getJobId(),
                jobId -> getStatus(jobId, applicantId))));
    }

    public PageResponse<ApplicationResponse> getJobApplications(UUID jobId, UUID userId, int page, int size) {
        var job = jobClient.getSummary(jobId);
        if (!job.clientId().equals(userId)) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "JOB_ACCESS_DENIED",
                    "Only the job owner can view its applicants");
        }
        var applications = repository.findByJobId(jobId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.from(applications, application -> new ApplicationResponse(
                application.getId(), application.getJobId(), application.getApplicantId(),
                application.getCreatedAt(), getStatus(job, application.getApplicantId())));
    }

    private String getStatus(UUID jobId, UUID applicantId) {
        JobClient.JobSummary job;
        try {
            job = jobClient.getSummary(jobId);
        } catch (ApplicationException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND
                    && "JOB_NOT_FOUND".equals(exception.getCode())) {
                return "CLOSED";
            }
            throw exception;
        }
        return getStatus(job, applicantId);
    }

    private String getStatus(JobClient.JobSummary job, UUID applicantId) {
        return switch (job.status()) {
            case "OPEN" ->
                "PENDING";
            case "CANCELLED" ->
                "CLOSED";
            case "ASSIGNED", "COMPLETED" -> {
                if (job.assignedWorkerId() == null) {
                    throw new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE,
                            "JOB_SERVICE_UNAVAILABLE", "Job assignment is unavailable. Please retry.");
                }
                yield applicantId.equals(job.assignedWorkerId()) ? "ACCEPTED" : "NOT_SELECTED";
            }
            default ->
                throw new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE,
                        "JOB_SERVICE_UNAVAILABLE", "Unknown job state. Please retry.");
        };
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
