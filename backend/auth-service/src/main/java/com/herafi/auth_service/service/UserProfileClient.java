package com.herafi.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.herafi.auth_service.dto.CreateUserProfileRequest;
import com.herafi.auth_service.exception.UserServiceUnavailableException;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserProfileClient {
    private final RestClient restClient;
    private final String internalApiKey;

    public UserProfileClient(
            RestClient userServiceRestClient,
            @Value("${services.internal-api-key}") String internalApiKey) {
        this.restClient = userServiceRestClient;
        this.internalApiKey = internalApiKey;
    }


    public void createProfile(CreateUserProfileRequest request)
    {
        try {
            restClient.post()
                    .uri("/internal/users")
                    .header("X-Internal-Api-Key", internalApiKey)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception)
        {
            log.atError().addKeyValue("event", "internal_call_failed").setCause(exception)
                    .log("User service call failed during profile creation");
            throw new UserServiceUnavailableException(exception);
        }
    }
}
