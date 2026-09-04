package com.herafi.user_service.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.ProfileEmailAlreadyInUseException;
import com.herafi.user_service.exception.UserProfileAlreadyExistsException;
import com.herafi.user_service.exception.UserProfileNotFoundException;
import com.herafi.user_service.repository.UserProfileRepository;

@Service
public class UserProfileService {

    private final UserProfileRepository repository;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserProfile create(CreateUserProfileRequest request) {
        if (repository.existsById(request.id())) {
            throw new UserProfileAlreadyExistsException();
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            throw new ProfileEmailAlreadyInUseException();
        }

        UserProfile profile = new UserProfile();
        profile.setId(request.id());
        profile.setEmail(email);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());

        return repository.save(profile);
    }

    @Transactional(readOnly = true)
    public UserProfile get(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    @Transactional
    public UserProfile update(UUID userId, UpdateUserProfileRequest request) {
        UserProfile profile = get(userId);
        profile.setFirstName(request.firstName().trim());
        profile.setLastName(request.lastName().trim());

        return repository.save(profile);
    }

    @Transactional
    public void recordHeartbeat(UUID userId) {
        UserProfile userProfile = repository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
        userProfile.setLastSeenAt(Instant.now());
    }
}
