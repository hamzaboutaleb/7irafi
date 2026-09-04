package com.herafi.job_service.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.herafi.job_service.entity.Job;
import com.herafi.job_service.entity.JobStatus;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsOptimisticLockFailureToConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH",
                "/internal/jobs/%s/assign".formatted(UUID.randomUUID()));

        ResponseEntity<ApiErrorResponse> response = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException(Job.class, UUID.randomUUID()),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("JOB_NOT_OPEN", response.getBody().code());
        assertEquals("Job is no longer open", response.getBody().message());
        assertEquals(request.getRequestURI(), response.getBody().path());
    }

    @Test
    void mapsAccessDeniedToNotOwner() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/jobs/123/cancel");

        ResponseEntity<ApiErrorResponse> response = handler.handleJobAccessDenied(
                new JobAccessDeniedException("Not owner"),
                request);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("NOT_OWNER", response.getBody().code());
        assertEquals("Not owner", response.getBody().message());
        assertEquals(request.getRequestURI(), response.getBody().path());
    }

    @Test
    void mapsInvalidJobStateToInvalidState() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/jobs/123/complete");

        ResponseEntity<ApiErrorResponse> response = handler.handleJobStateConflict(
                new InvalidJobStateException(JobStatus.OPEN),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("INVALID_STATE", response.getBody().code());
        assertEquals(request.getRequestURI(), response.getBody().path());
    }

    @Test
    void mapsJobNotOpenToJobNotOpen() {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/internal/jobs/123/assign");

        ResponseEntity<ApiErrorResponse> response = handler.handleJobNotOpen(
                new JobNotOpenException(),
                request);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("JOB_NOT_OPEN", response.getBody().code());
        assertEquals("Job must be OPEN to be assigned", response.getBody().message());
        assertEquals(request.getRequestURI(), response.getBody().path());
    }

    @Test
    void mapsInvalidReferenceToInvalidReference() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/jobs");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidReference(
                new InvalidReferenceException("Unknown or inactive skill: UNKNOWN"),
                request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_REFERENCE", response.getBody().code());
        assertEquals("Unknown or inactive skill: UNKNOWN", response.getBody().message());
        assertEquals(request.getRequestURI(), response.getBody().path());
    }

    @Test
    void mapsMaxUploadSizeToApiErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/jobs/123/images");

        ResponseEntity<ApiErrorResponse> response = handler.handleMaxSizeException(
                new MaxUploadSizeExceededException(20),
                request);

        assertEquals(413, response.getStatusCode().value());
        assertEquals("INVALID_IMAGE", response.getBody().code());
        assertEquals("File too large. Maximum upload size is 10MB.", response.getBody().message());
        assertEquals(request.getRequestURI(), response.getBody().path());
    }
}
