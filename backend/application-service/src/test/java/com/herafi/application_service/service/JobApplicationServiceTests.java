package com.herafi.application_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.herafi.application_service.client.JobClient;
import com.herafi.application_service.client.UserClient;
import com.herafi.application_service.entity.JobApplication;
import com.herafi.application_service.exceptions.ApplicationException;
import com.herafi.application_service.repository.JobApplicationRepository;

class JobApplicationServiceTests {
    private final JobApplicationRepository repository = mock(JobApplicationRepository.class);
    private final JobClient jobs = mock(JobClient.class);
    private final UserClient users = mock(UserClient.class);
    private final JobApplicationService service = new JobApplicationService(repository, jobs, users);

    @Test
    void refusesApplicationWhenEligibilityIsDeniedOrUnavailable() {
        UUID applicantId = UUID.randomUUID();
        for (HttpStatus status : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.SERVICE_UNAVAILABLE}) {
            var failure = new ApplicationException(status, "ELIGIBILITY_FAILURE", "Denied");
            doThrow(failure).when(users).requireActiveWorker(applicantId);
            assertSame(failure, assertThrows(ApplicationException.class,
                    () -> service.apply(UUID.randomUUID(), applicantId)));
            verifyNoInteractions(jobs, repository);
        }
    }

    @Test
    void activeWorkerCanApplyToAnotherOwnersOpenJob() {
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        when(jobs.getSummary(jobId)).thenReturn(
                new JobClient.JobSummary(jobId, UUID.randomUUID(), "OPEN", null));
        when(repository.saveAndFlush(any(JobApplication.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.apply(jobId, applicantId);

        assertEquals(applicantId, result.applicantId());
        assertEquals("PENDING", result.status());
        var order = inOrder(users, jobs, repository);
        order.verify(users).requireActiveWorker(applicantId);
        order.verify(jobs).getSummary(jobId);
        order.verify(repository).existsByJobIdAndApplicantId(jobId, applicantId);
        order.verify(repository).saveAndFlush(any(JobApplication.class));
    }

    @Test
    void activeWorkerStillCannotApplyToOwnJob() {
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        when(jobs.getSummary(jobId)).thenReturn(
                new JobClient.JobSummary(jobId, applicantId, "OPEN", null));
        var error = assertThrows(ApplicationException.class, () -> service.apply(jobId, applicantId));
        assertEquals("OWN_JOB_APPLICATION", error.getCode());
        verifyNoInteractions(repository);
    }
}
