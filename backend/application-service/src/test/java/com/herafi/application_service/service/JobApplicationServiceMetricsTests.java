package com.herafi.application_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import com.herafi.application_service.client.JobClient;
import com.herafi.application_service.client.NotificationClient;
import com.herafi.application_service.client.UserClient;
import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.repository.JobApplicationRepository;

class JobApplicationServiceMetricsTests {

    @Test
    void applyIncrementsTheApplicationsSubmittedCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobApplicationRepository repository = mock(JobApplicationRepository.class);
        JobClient jobClient = mock(JobClient.class);
        UserClient userClient = mock(UserClient.class);
        NotificationClient notificationClient = mock(NotificationClient.class);
        ApplicationSubmissionService submissionService = mock(ApplicationSubmissionService.class);

        JobApplicationService service = new JobApplicationService(
                repository, jobClient, userClient, notificationClient, registry, submissionService);

        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        when(jobClient.getSummary(jobId))
                .thenReturn(new JobClient.JobSummary(jobId, clientId, "OPEN", null, "PLUMBING", "CASABLANCA"));
        when(submissionService.submit(any(JobClient.JobSummary.class), any(UUID.class)))
                .thenReturn(new JobApplication());

        service.apply(jobId, applicantId);

        assertThat(registry.counter("herafi.applications.submitted").count()).isEqualTo(1.0);
    }

    @Test
    void acceptIncrementsTheApplicationsAcceptedCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobApplicationRepository repository = mock(JobApplicationRepository.class);
        JobClient jobClient = mock(JobClient.class);
        UserClient userClient = mock(UserClient.class);
        NotificationClient notificationClient = mock(NotificationClient.class);
        ApplicationSubmissionService submissionService = mock(ApplicationSubmissionService.class);

        JobApplicationService service = new JobApplicationService(
                repository, jobClient, userClient, notificationClient, registry, submissionService);

        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setApplicantId(applicantId);

        when(repository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobClient.getSummary(jobId))
                .thenReturn(new JobClient.JobSummary(jobId, clientId, "OPEN", null, "PLUMBING", "CASABLANCA"));
        when(jobClient.assignWorker(jobId, applicantId))
                .thenReturn(new JobClient.JobSummary(jobId, clientId, "ASSIGNED", applicantId, "PLUMBING", "CASABLANCA"));

        service.accept(applicationId, clientId);

        assertThat(registry.counter("herafi.applications.accepted").count()).isEqualTo(1.0);
    }
}
