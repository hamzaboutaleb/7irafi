package com.herafi.user_service.service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public PublicUserProfileService(UserProfileService users,
            ProfessionalProfileRepository professionals, AvatarService avatars) {
        this.users = users;
        this.professionals = professionals;
        this.avatars = avatars;
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
        return new PublicUserProfileResponse(user.getId(), user.getFirstName(), user.getLastName(),
                avatars.urlFor(user), categories, cities);
    }
}
