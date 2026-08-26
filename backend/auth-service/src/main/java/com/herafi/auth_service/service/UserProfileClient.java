package com.herafi.auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.herafi.auth_service.dto.CreateUserProfileRequest;
import com.herafi.auth_service.exception.UserServiceUnavailableException;

@Service
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
            throw new UserServiceUnavailableException(exception);
        }
    }
}
