package com.herafi.rating_service.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.herafi.rating_service.exception.JobNotCompletedException;
import com.herafi.rating_service.exception.JobServiceUnavailableException;
import com.herafi.rating_service.exception.RatingException;

@Component
public class JobClient {

    private static final Logger log = LoggerFactory.getLogger(JobClient.class);

    private final RestClient client;

    public JobClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${service.security.internal-api-key}") String internalApiKey) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        this.client = builder
                .baseUrl("http://JOB-SERVICE")
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public JobSummary validateJobCompleted(UUID jobId, UUID raterId) {
        try {
            JobSummary response = client.get()
                    .uri("/internal/jobs/{jobId}/summary", jobId)
                    .retrieve()
                    .body(JobSummary.class);

            if (response == null || !jobId.equals(response.id()) || response.clientId() == null
                    || response.status() == null) {
                throw new JobServiceUnavailableException();
            }
            if (!"COMPLETED".equals(response.status()) || response.assignedWorkerId() == null) {
                throw new JobNotCompletedException();
            }
            if (!raterId.equals(response.clientId()) && !raterId.equals(response.assignedWorkerId())) {
                throw new RatingException("INVALID_RATING_PARTICIPANT", "Only job participants can submit a rating");
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new RatingException("JOB_NOT_FOUND", "Job not found");
            }
            log.warn("Job summary lookup failed with HTTP {}", ex.getStatusCode().value());
            throw new JobServiceUnavailableException();
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Job service validation failed for job {}: {}", jobId, ex.getMessage());
            throw new JobServiceUnavailableException();
        }
    }

    public record JobSummary(UUID id, UUID clientId, String status, UUID assignedWorkerId) {
    }
}
