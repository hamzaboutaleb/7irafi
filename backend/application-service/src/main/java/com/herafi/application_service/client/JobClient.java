package com.herafi.application_service.client;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Map;
import org.springframework.http.MediaType;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.herafi.application_service.config.RequestIdInterceptor;
import com.herafi.application_service.exceptions.ApplicationException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JobClient {

    private final RestClient client;

    public JobClient(@Value("${services.job.url}") String baseUrl,
            @Value("${service.security.internal-api-key}") String internalApiKey,
            RequestIdInterceptor requestIdInterceptor) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor(requestIdInterceptor)
                .build();
    }

    public JobSummary getSummary(UUID jobId) {
        try {
            JobSummary summary = client.get().uri("/internal/jobs/{id}/summary", jobId)
                    .retrieve().body(JobSummary.class);
            if (summary == null || !jobId.equals(summary.id()) || summary.clientId() == null || summary.status() == null) {
                throw unavailable();
            }
            return summary;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ApplicationException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found");
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public JobSummary assignWorker(UUID jobId, UUID workerId) {
        try {
            JobSummary summary = client.patch().uri("/internal/jobs/{id}/assign", jobId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("workerId", workerId))
                    .retrieve().body(JobSummary.class);
            if (summary == null || !jobId.equals(summary.id()) || summary.clientId() == null
                    || !"ASSIGNED".equals(summary.status()) || !workerId.equals(summary.assignedWorkerId())) {
                throw unavailable();
            }
            return summary;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ApplicationException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Job not found");
            }
            if (exception.getStatusCode().value() == 409) {
                throw new ApplicationException(HttpStatus.CONFLICT, "JOB_STATE_CONFLICT",
                        "Job can no longer be assigned. Refresh and retry.");
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private ApplicationException unavailable() {
        return unavailable(null);
    }

    private ApplicationException unavailable(RestClientException cause) {
        log.atError().addKeyValue("event", "internal_call_failed").setCause(cause)
                .log("Job service call failed");
        return new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE,
                "JOB_SERVICE_UNAVAILABLE", "Unable to confirm the job operation. Please retry.");
    }

    public record JobSummary(
            UUID id,
            UUID clientId,
            String status,
            UUID assignedWorkerId) {

    }
}
