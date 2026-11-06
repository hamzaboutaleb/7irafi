package com.herafi.rating_service.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.herafi.rating_service.exception.RatingException;
import com.herafi.rating_service.exception.UserNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UserClient {

    private final RestClient client;

    /** Shares the common HTTP settings, but keeps this client's address on its own builder copy. */
    public UserClient(@LoadBalanced RestClient.Builder builder) {
        this.client = builder.clone()
                .baseUrl("http://USER-SERVICE")
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
            log.warn("User existence lookup failed for user {} with HTTP {}", userId, ex.getStatusCode().value());
            throw new RatingException("USER_SERVICE_UNAVAILABLE", "Unable to confirm the user. Please try again.");
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("User service validation failed for user {}: {}", userId, ex.getMessage());
            throw new RatingException("USER_SERVICE_UNAVAILABLE", "Unable to confirm the user. Please try again.");
        }
    }

    /**
     * Fetches names for a review page in batches of at most 100. Missing profiles are
     * left out of the result. If names cannot be loaded, return an empty map so
     * callers can still show reviews with a translated fallback label.
     * This fallback is only for display names; validateUserExists must still fail
     * when a required user check cannot be completed.
     */
    public Map<UUID, String> getDisplayNames(Set<UUID> userIds) {
        // A page can contain 100 reviews and up to 200 different participants.
        // Keep each request within the user service's limit of 100 IDs.
        List<UUID> ids = List.copyOf(userIds);
        Map<UUID, String> names = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 100) {
            names.putAll(getDisplayNameBatch(Set.copyOf(ids.subList(start, Math.min(start + 100, ids.size())))));
        }
        return names;
    }

    private Map<UUID, String> getDisplayNameBatch(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<UserDisplayName> profiles = client.get()
                    .uri(uri -> uri.path("/internal/users/names").queryParam("ids", userIds).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserDisplayName>>() {});
            if (profiles == null) {
                log.warn("User service returned no body for a display-name lookup");
                return Map.of();
            }
            Map<UUID, String> names = new HashMap<>();
            for (UserDisplayName profile : profiles) {
                if (profile != null && profile.id() != null && userIds.contains(profile.id())
                        && profile.displayName() != null && !profile.displayName().isBlank()) {
                    names.put(profile.id(), profile.displayName());
                }
            }
            return names;
        } catch (RestClientResponseException ex) {
            log.warn("Display-name lookup failed for {} users with HTTP {}", userIds.size(), ex.getStatusCode().value());
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Display-name lookup failed for {} users: {}", userIds.size(), ex.getMessage());
        }
        return Map.of();
    }

    /** Only names and IDs are needed here; other profile fields are not requested. */
    public record UserDisplayName(UUID id, String displayName) {
    }
}
