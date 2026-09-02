package com.herafi.user_service.dto;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.herafi.user_service.entity.ProfessionalProfile;

public record ProfessionalCardResponse(
        UUID userId,
        String firstName,
        Set<ServiceCategoryResponse> categories,
        Set<ServiceCityResponse> cities) {

    public static ProfessionalCardResponse from(ProfessionalProfile professionalProfile) {
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

        return new ProfessionalCardResponse(
                professionalProfile.getUserId(),
                professionalProfile.getUserProfile().getFirstName(),
                categoryResponses,
                cityResponses);
    }
}
