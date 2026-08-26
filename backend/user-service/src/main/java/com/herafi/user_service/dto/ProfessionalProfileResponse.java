package com.herafi.user_service.dto;

import java.util.UUID;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;

public record ProfessionalProfileResponse(
        UUID userId,
        String biography,
        ProfessionalOnboardingStatus onboardingStatus) {
    public static ProfessionalProfileResponse from(ProfessionalProfile profile) {
        return new ProfessionalProfileResponse(
                profile.getUserId(),
                profile.getBiography(),
                profile.getOnboardingStatus());
    }
}
