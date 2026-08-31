package com.herafi.user_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, UUID> {

    @EntityGraph(attributePaths = { "categories", "cities" })
    Optional<ProfessionalProfile> findByUserId(UUID userId);

    @EntityGraph(attributePaths = { "categories", "cities", "userProfile" })
    Page<ProfessionalProfile> findByOnboardingStatus(ProfessionalOnboardingStatus professionalOnboardingStatus,
            Pageable page);
}
