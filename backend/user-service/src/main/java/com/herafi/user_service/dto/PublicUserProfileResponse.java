package com.herafi.user_service.dto;

import java.util.Set;
import java.util.UUID;

/** Identity and published services only; account contact details stay private. */
public record PublicUserProfileResponse(
        UUID id,
        String firstName,
        String lastName,
        String avatarUrl,
        Set<ServiceCategoryResponse> categories,
        Set<ServiceCityResponse> cities) {
}
