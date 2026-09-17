package com.herafi.user_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.herafi.user_service.entity.ProfessionalOnboardingStatus;
import com.herafi.user_service.entity.ProfessionalProfile;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, UUID> {

    @EntityGraph(attributePaths = { "categories", "cities" })
    Optional<ProfessionalProfile> findByUserId(UUID userId);

    @EntityGraph(attributePaths = { "categories", "cities", "userProfile" })
    Optional<ProfessionalProfile> findByUserIdAndOnboardingStatus(
            UUID userId, ProfessionalOnboardingStatus status);

    /**
     * Directory read query. A null categoryCode or cityCode means "no filter on
     * that field", so one method covers all four combinations.
     */
    @EntityGraph(attributePaths = { "categories", "cities", "userProfile" })
    @Query(value = """
            SELECT DISTINCT p
            FROM ProfessionalProfile p
            JOIN p.categories c
            JOIN p.cities ct
            WHERE p.onboardingStatus = :status
              AND (:categoryCode IS NULL OR c.code = :categoryCode)
              AND (:cityCode IS NULL OR ct.code = :cityCode)
            """, countQuery = """
            SELECT COUNT(DISTINCT p)
            FROM ProfessionalProfile p
            JOIN p.categories c
            JOIN p.cities ct
            WHERE p.onboardingStatus = :status
              AND (:categoryCode IS NULL OR c.code = :categoryCode)
              AND (:cityCode IS NULL OR ct.code = :cityCode)
            """)
    Page<ProfessionalProfile> findDirectoryPage(
            @Param("status") ProfessionalOnboardingStatus status,
            @Param("categoryCode") String categoryCode,
            @Param("cityCode") String cityCode,
            Pageable pageable);
}
