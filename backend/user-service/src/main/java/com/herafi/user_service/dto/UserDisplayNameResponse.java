package com.herafi.user_service.dto;

import java.util.UUID;

import com.herafi.user_service.entity.UserProfile;

/** A small internal response for review labels; it does not expose email or other profile details. */
public record UserDisplayNameResponse(UUID id, String displayName) {
    public static UserDisplayNameResponse from(UserProfile profile) {
        return new UserDisplayNameResponse(profile.getId(),
                (profile.getFirstName() + " " + profile.getLastName()).strip());
    }
}
