package com.herafi.user_service.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.herafi.user_service.entity.ProfessionalProfile;

public record PublicProfessionalProfileResponse(
        UUID userId,
        String firstName,
        String biography,
        Set<ServiceCategoryResponse> categories,
        Set<ServiceCityResponse> cities,
        boolean online,
        String avatarUrl) {

    public static PublicProfessionalProfileResponse from(ProfessionalProfile profile, Instant onlineCutoff) {
        return from(profile, onlineCutoff, null);
    }

    public static PublicProfessionalProfileResponse from(
            ProfessionalProfile profile,
            Instant onlineCutoff,
            String avatarUrl) {
        ProfessionalCardResponse card = ProfessionalCardResponse.from(profile, onlineCutoff, avatarUrl);
        return new PublicProfessionalProfileResponse(card.userId(), card.firstName(), profile.getBiography(),
                card.categories(), card.cities(), card.online(), card.avatarUrl());
    }
}
