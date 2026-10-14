package com.herafi.rating_service.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.herafi.rating_service.exception.JobNotCompletedException;
import com.herafi.rating_service.exception.JobServiceUnavailableException;

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

    public void validateJobCompleted(UUID jobId, UUID raterId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.get()
                    .uri("/internal/jobs/{jobId}/validate-completion", jobId)
                    .header("X-Rater-Id", raterId.toString())
                    .retrieve()
                    .toEntity(Map.class)
                    .getBody();

            if (response == null || !Boolean.TRUE.equals(response.get("completed"))) {
                throw new JobNotCompletedException();
            }
        } catch (RestClientException ex) {
            log.warn("Job service validation failed for job {}: {}", jobId, ex.getMessage());
            throw new JobServiceUnavailableException();
        }
    }
}