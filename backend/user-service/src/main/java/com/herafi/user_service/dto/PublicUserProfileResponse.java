package com.herafi.user_service.dto;

import java.util.Set;
import java.util.UUID;

/** Public identity, published services, and current presence. */
public record PublicUserProfileResponse(
        UUID id,
        String firstName,
        String lastName,
        String avatarUrl,
        Set<ServiceCategoryResponse> categories,
        Set<ServiceCityResponse> cities,
        boolean online) {
}
