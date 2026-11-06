package com.herafi.rating_service.client;

import java.util.UUID;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.herafi.rating_service.exception.JobServiceUnavailableException;
import com.herafi.rating_service.exception.RatingException;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches job information from the job service for the rating service.
 * This class handles HTTP calls and checks that their responses are usable.
 * RatingService decides whether the job and its participants can be rated.
 */
@Component
@Slf4j
public class JobClient {

    private final RestClient client;

    /**
     * Uses the timeouts and internal key configured once in ServiceClientConfig.
     *
     * The @LoadBalanced parameter selects the builder configured for service
     * discovery. It lets us use JOB-SERVICE instead of a fixed host and port:
     * Spring looks up an available instance through Eureka.
     */
    public JobClient(@LoadBalanced RestClient.Builder builder) {
        // Clone the shared builder so setting our address cannot affect UserClient.
        this.client = builder.clone()
                .baseUrl("http://JOB-SERVICE")
                .build();
    }

    /**
     * Fetches the requested job and returns its basic information.
     * Unfinished jobs are valid responses too; completion and participant checks
     * belong in RatingService.
     *
     * A 404 means the job is missing. Other HTTP errors, connection failures, or
     * unusable responses mean we cannot reliably obtain the job information.
     */
    public JobSummary getSummary(UUID jobId) {
        JobSummary response;
        try {
            response = client.get()
                    .uri("/internal/jobs/{jobId}/summary", jobId)
                    .retrieve()
                    .body(JobSummary.class);
        } catch (RestClientResponseException ex) {
            // The service answered with an HTTP error. Keep "job missing"
            // separate from "we could not check the job".
            if (ex.getStatusCode().value() == 404) {
                throw new RatingException("JOB_NOT_FOUND", "Job not found");
            }
            log.warn("Job summary lookup failed for job {} with HTTP {}", jobId, ex.getStatusCode().value());
            throw new JobServiceUnavailableException();
        } catch (RestClientException | IllegalStateException ex) {
            // RestClientException covers failures such as timeouts and connection
            // errors. The load balancer can throw IllegalStateException when no
            // service instance is available. Keep this catch around the HTTP call
            // only, so later validation code is not covered by that broad catch.
            log.warn("Job service validation failed for job {}: {}", jobId, ex.getMessage());
            throw new JobServiceUnavailableException();
        }

        // A successful HTTP response can still contain missing or incorrect data.
        // Reject it rather than let rating decisions use the wrong job or null fields.
        // A missing worker is allowed here because an open job may not have one yet.
        if (response == null || !jobId.equals(response.id()) || response.clientId() == null
                || response.status() == null) {
            log.warn("Job service returned an incomplete or mismatched summary for job {}", jobId);
            throw new JobServiceUnavailableException();
        }
        return response;
    }

    /**
     * The fields we read from the job service's JSON response.
     * Keeping this small record here avoids depending on the job service's Java
     * classes. Its status is read as text, and its worker ID may be null.
     */
    public record JobSummary(UUID id, UUID clientId, String status, UUID assignedWorkerId) {
    }
}
