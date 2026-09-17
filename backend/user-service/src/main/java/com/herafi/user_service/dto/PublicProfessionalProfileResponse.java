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
        boolean online) {

    public static PublicProfessionalProfileResponse from(ProfessionalProfile profile, Instant onlineCutoff) {
        ProfessionalCardResponse card = ProfessionalCardResponse.from(profile, onlineCutoff);
        return new PublicProfessionalProfileResponse(card.userId(), card.firstName(), profile.getBiography(),
                card.categories(), card.cities(), card.online());
    }
}
