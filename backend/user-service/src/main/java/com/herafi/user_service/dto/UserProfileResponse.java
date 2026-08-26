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
        Instant updatedAt
) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
