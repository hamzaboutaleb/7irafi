package com.herafi.user_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.CreateProfessionalProfileRequest;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.ProfessionalProfileAlreadyExistsException;
import com.herafi.user_service.exception.UserProfileNotFoundException;
import com.herafi.user_service.repository.ProfessionalProfileRepository;
import com.herafi.user_service.repository.UserProfileRepository;

@Service
public class ProfessionalProfileService {
    private final UserProfileRepository userProfileRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;

    public ProfessionalProfileService(UserProfileRepository userProfileRepository,
            ProfessionalProfileRepository professionalProfileRepository) {
        this.userProfileRepository = userProfileRepository;
        this.professionalProfileRepository = professionalProfileRepository;
    }

    @Transactional
    public ProfessionalProfile create(UUID userId, CreateProfessionalProfileRequest request) {
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));

        if (professionalProfileRepository.existsById(userId)) {
            throw new ProfessionalProfileAlreadyExistsException(userId);
        }
        ProfessionalProfile pp = new ProfessionalProfile();

        pp.setBiography(request.biography().trim());
        pp.setUserProfile(userProfile);
        return professionalProfileRepository.save(pp);
    }
}
