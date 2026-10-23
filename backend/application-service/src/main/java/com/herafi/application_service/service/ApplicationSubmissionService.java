package com.herafi.application_service.service;

import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.application_service.client.JobClient;
import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.entity.JobLifecycleState;
import com.herafi.application_service.event.ApplicationAnalyticsEvent;
import com.herafi.application_service.event.JobLifecycleEvent;
import com.herafi.application_service.exceptions.ApplicationException;
import com.herafi.application_service.repository.JobApplicationRepository;
import com.herafi.application_service.repository.JobLifecycleStateRepository;

import lombok.RequiredArgsConstructor;

/** Local transaction only: HTTP eligibility/job checks happen before entering this service. */
@Service
@RequiredArgsConstructor
public class ApplicationSubmissionService {

    private final JobApplicationRepository applications;
    private final JobLifecycleStateRepository jobStates;
    private final OutboxEventWriter writer;

    @Transactional
    public JobApplication submit(JobClient.JobSummary job, UUID applicantId) {
        jobStates.ensureExists(job.id());
        JobLifecycleState state = jobStates.lockByJobId(job.id());
        // Do not miss a closure that the consumer processed after the HTTP summary was read.
        if (state.isDeleted() || (state.getStatus() != null && state.getStatus() != JobLifecycleEvent.Status.OPEN)) {
            throw new ApplicationException(HttpStatus.CONFLICT, "JOB_NOT_OPEN",
                    "Applications are only allowed for open jobs");
        }
        if (applications.existsByJobIdAndApplicantId(job.id(), applicantId)) {
            throw duplicate();
        }

        JobApplication application = new JobApplication();
        application.setJobId(job.id());
        application.setApplicantId(applicantId);
        application.setJobSkill(job.skill());
        application.setJobCity(job.city());
        try {
            applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_job_applications_job_applicant".equals(violation.getConstraintName())) {
                    throw duplicate();
                }
            }
            throw exception;
        }
        writer.recordApplicationEvent(application, ApplicationAnalyticsEvent.Type.APPLICATION_SUBMITTED,
                application.getCreatedAt());
        return application;
    }

    private ApplicationException duplicate() {
        return new ApplicationException(HttpStatus.CONFLICT, "DUPLICATE_APPLICATION",
                "You have already applied to this job");
    }
}
