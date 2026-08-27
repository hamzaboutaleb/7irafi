package com.herafi.user_service.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.herafi.user_service.dto.CreateProfessionalProfileRequest;
import com.herafi.user_service.dto.UpdateProfessionalProfileRequest;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.entity.ServiceCategory;
import com.herafi.user_service.entity.ServiceCity;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.exception.InvalidCategoryException;
import com.herafi.user_service.exception.InvalidCityException;
import com.herafi.user_service.exception.ProfessionalProfileAlreadyExistsException;
import com.herafi.user_service.exception.ProfessionalProfileNotFoundException;
import com.herafi.user_service.exception.UserProfileNotFoundException;
import com.herafi.user_service.repository.ProfessionalProfileRepository;
import com.herafi.user_service.repository.ServiceCategoryRepository;
import com.herafi.user_service.repository.ServiceCityRepository;
import com.herafi.user_service.repository.UserProfileRepository;

@Service
public class ProfessionalProfileService {
    private final UserProfileRepository userProfileRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceCityRepository serviceCityRepository;

    public ProfessionalProfileService(UserProfileRepository userProfileRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ServiceCategoryRepository serviceCategoryRepository,
            ServiceCityRepository serviceCityRepository) {
        this.userProfileRepository = userProfileRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.serviceCategoryRepository = serviceCategoryRepository;
        this.serviceCityRepository = serviceCityRepository;
    }

    private Set<ServiceCategory> resolveCategories(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new InvalidCategoryException("At least one service category code is required");
        }

        Set<String> normalizedCodes = codes.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());

        List<ServiceCategory> found = serviceCategoryRepository.findAllByCodeInAndActiveTrue(normalizedCodes);

        if (found.size() != normalizedCodes.size()) {
            Set<String> foundCodes = found.stream()
                    .map(ServiceCategory::getCode)
                    .collect(Collectors.toSet());

            Set<String> missingCodes = new HashSet<>(normalizedCodes);
            missingCodes.removeAll(foundCodes);

            throw new InvalidCategoryException("Unknown or inactive category codes: " + missingCodes);
        }

        return new HashSet<>(found);
    }

    private Set<ServiceCity> resolveCities(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new InvalidCityException("At least one service category code is required");
        }

        Set<String> nomalizedCodes = codes.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());

        List<ServiceCity> found = serviceCityRepository.findAllByCodeInAndActiveTrue(nomalizedCodes);

        if (found.size() != nomalizedCodes.size()) {
            Set<String> foundCodes = found.stream()
                    .map(ServiceCity::getCode)
                    .collect(Collectors.toSet());

            Set<String> missingCodes = new HashSet<>(nomalizedCodes);
            missingCodes.removeAll(foundCodes);
            throw new InvalidCityException("Unknown or inactive City codes: " + missingCodes);

        }

        return new HashSet<>(found);
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

    @Transactional(readOnly = true)
    public ProfessionalProfile get(UUID userId) {
        return professionalProfileRepository.findById(userId)
                .orElseThrow(() -> new ProfessionalProfileNotFoundException(userId));
    }

    @Transactional
    public ProfessionalProfile update(UUID userId, UpdateProfessionalProfileRequest request) {
        ProfessionalProfile professionalProfile = professionalProfileRepository.findById(userId)
                .orElseThrow(() -> new ProfessionalProfileNotFoundException(userId));
        professionalProfile.setBiography(request.biography().trim());
        return professionalProfileRepository.save(professionalProfile);
    }

}
