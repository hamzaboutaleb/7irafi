package com.herafi.user_service.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "professional_profiles")
public class ProfessionalProfile {

    @Id
    private UUID userId;

    @MapsId()
    @JoinColumn(name = "user_id", nullable = false)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private UserProfile userProfile;

    @Column(nullable = false, length = 2000)
    private String biography;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, name = "onboarding_status")
    private ProfessionalOnboardingStatus onboardingStatus = ProfessionalOnboardingStatus.INCOMPLETE;

}
