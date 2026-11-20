package com.herafi.application_service.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.herafi.shared.observability.RequestIdInterceptor;
import com.herafi.application_service.exceptions.ApplicationException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UserClient {
    private final RestClient client;

    public UserClient(@Value("${services.user.url}") String baseUrl,
            @Value("${service.security.internal-api-key}") String internalApiKey,
            RequestIdInterceptor requestIdInterceptor) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestInterceptor(requestIdInterceptor)
                .build();
    }

    public void requireActiveWorker(UUID userId) {
        WorkerEligibility result;
        try {
            result = client.get().uri("/internal/users/{id}/worker-eligibility", userId)
                    .retrieve().body(WorkerEligibility.class);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
        if (result == null || !userId.equals(result.userId()) || result.eligible() == null) {
            throw unavailable();
        }
        if (!result.eligible()) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, "WORKER_REQUIRED",
                    "Only workers with an active professional profile can apply to jobs");
        }
    }

    private ApplicationException unavailable() {
        return unavailable(null);
    }

    private ApplicationException unavailable(RestClientException cause) {
        log.atError().addKeyValue("event", "internal_call_failed").setCause(cause)
                .log("User service call failed");
        return new ApplicationException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                "Unable to verify worker eligibility. Please retry.");
    }

    public record WorkerEligibility(UUID userId, Boolean eligible) {
    }
}
