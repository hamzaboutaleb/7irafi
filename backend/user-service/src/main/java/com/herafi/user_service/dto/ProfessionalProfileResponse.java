package com.herafi.user_service.dto;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;

public record ProfessionalProfileResponse(
        UUID userId,
        String biography,
        ProfessionalOnboardingStatus onboardingStatus,
        Set<ServiceCategoryResponse> categories,
        Set<ServiceCityResponse> cities) {

    public static ProfessionalProfileResponse from(ProfessionalProfile profile) {
        Set<ServiceCategoryResponse> categoryResponses = profile.getCategories() == null
                ? Set.of()
                : profile.getCategories().stream()
                        .map(ServiceCategoryResponse::from)
                        .collect(Collectors.toUnmodifiableSet());

        Set<ServiceCityResponse> cityResponses = profile.getCities() == null
                ? Set.of()
                : profile.getCities().stream()
                        .map(ServiceCityResponse::from)
                        .collect(Collectors.toUnmodifiableSet());

        return new ProfessionalProfileResponse(
                profile.getUserId(),
                profile.getBiography(),
                profile.getOnboardingStatus(),
                categoryResponses,
                cityResponses);
    }
}
