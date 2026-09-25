package com.herafi.user_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.herafi.user_service.entity.UserProfile;

public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Instant createdAt,
        Instant updatedAt,
        /** Short-lived signed link, null when the person has no avatar. */
        String avatarUrl
) {

    public static UserProfileResponse from(UserProfile profile) {
        return from(profile, null);
    }

    public static UserProfileResponse from(UserProfile profile, String avatarUrl) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getCreatedAt(),
                profile.getUpdatedAt(),
                avatarUrl
        );
    }
}
