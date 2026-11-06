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

import com.herafi.rating_service.exception.RatingException;
import com.herafi.rating_service.exception.UserNotFoundException;

@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient client;

    public UserClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${service.security.internal-api-key}") String internalApiKey) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        this.client = builder
                .baseUrl("http://USER-SERVICE")
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public void validateUserExists(UUID userId) {
        try {
            client.get()
                    .uri("/internal/users/{userId}/exists", userId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new UserNotFoundException(userId);
            }
            throw new RatingException("USER_SERVICE_UNAVAILABLE", "Unable to confirm the user. Please try again.");
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("User service validation failed for user {}: {}", userId, ex.getMessage());
            throw new RatingException("USER_SERVICE_UNAVAILABLE", "Unable to confirm the user. Please try again.");
        }
    }
}
