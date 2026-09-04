package com.herafi.user_service.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.herafi.user_service.entity.ProfessionalProfile;

public record ProfessionalCardResponse(
        UUID userId,
        String firstName,
        Set<ServiceCategoryResponse> categories,
        Set<ServiceCityResponse> cities,
        boolean online) {

    /**
     * @param onlineCutoff the instant a heartbeat must be newer than to count as
     *                     online. Passed in rather than computed here so every card
     *                     in one response is judged against the same moment.
     */
    public static ProfessionalCardResponse from(
            ProfessionalProfile professionalProfile,
            Instant onlineCutoff) {
        Set<ServiceCategoryResponse> categoryResponses = professionalProfile.getCategories() == null
                ? Set.of()
                : professionalProfile.getCategories().stream()
                        .map(ServiceCategoryResponse::from)
                        .collect(Collectors.toUnmodifiableSet());

        Set<ServiceCityResponse> cityResponses = professionalProfile.getCities() == null
                ? Set.of()
                : professionalProfile.getCities().stream()
                        .map(ServiceCityResponse::from)
                        .collect(Collectors.toUnmodifiableSet());

        Instant lastSeenAt = professionalProfile.getUserProfile().getLastSeenAt();

        return new ProfessionalCardResponse(
                professionalProfile.getUserId(),
                professionalProfile.getUserProfile().getFirstName(),
                categoryResponses,
                cityResponses,
                lastSeenAt != null && lastSeenAt.isAfter(onlineCutoff));
    }
}
