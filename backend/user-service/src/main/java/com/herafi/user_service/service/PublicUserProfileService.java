package com.herafi.user_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.PublicUserProfileResponse;
import com.herafi.user_service.dto.ServiceCategoryResponse;
import com.herafi.user_service.dto.ServiceCityResponse;
import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.repository.ProfessionalProfileRepository;

@Service
public class PublicUserProfileService {
    private final UserProfileService users;
    private final ProfessionalProfileRepository professionals;
    private final AvatarService avatars;
    private final Duration onlineThreshold;

    public PublicUserProfileService(UserProfileService users,
            ProfessionalProfileRepository professionals, AvatarService avatars,
            @Value("${users.presence.online-threshold}") Duration onlineThreshold) {
        this.users = users;
        this.professionals = professionals;
        this.avatars = avatars;
        this.onlineThreshold = onlineThreshold;
    }

    @Transactional(readOnly = true)
    public PublicUserProfileResponse get(UUID userId) {
        UserProfile user = users.get(userId);
        var professional = professionals.findByUserIdAndOnboardingStatus(userId, ProfessionalOnboardingStatus.ACTIVE);
        Set<ServiceCategoryResponse> categories = professional
                .map(profile -> profile.getCategories().stream().map(ServiceCategoryResponse::from).collect(Collectors.toSet()))
                .orElseGet(Set::of);
        Set<ServiceCityResponse> cities = professional
                .map(profile -> profile.getCities().stream().map(ServiceCityResponse::from).collect(Collectors.toSet()))
                .orElseGet(Set::of);
        Instant lastSeenAt = user.getLastSeenAt();
        boolean online = lastSeenAt != null && lastSeenAt.isAfter(Instant.now().minus(onlineThreshold));
        return new PublicUserProfileResponse(user.getId(), user.getFirstName(), user.getLastName(),
                avatars.urlFor(user), categories, cities, online);
    }
}
