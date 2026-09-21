package com.herafi.user_service.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.PageResponse;
import com.herafi.user_service.dto.ProfessionalCardResponse;
import com.herafi.user_service.dto.PublicProfessionalProfileResponse;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.service.ProfessionalProfileService;

@RestController
@RequestMapping("/users/professionals")
public class ProfessionalDirectoryController {
    private final ProfessionalProfileService professionalProfileService;
    private final Duration onlineThreshold;

    public ProfessionalDirectoryController(
            ProfessionalProfileService professionalProfileService,
            @Value("${users.presence.online-threshold}") Duration onlineThreshold) {
        this.professionalProfileService = professionalProfileService;
        this.onlineThreshold = onlineThreshold;
    }

    private static String normalise(String code) {
        if (code == null || code.isBlank())
            return null;

        return code.strip();
    }

    @GetMapping("/{userId}")
    public ApiResponse<PublicProfessionalProfileResponse> getProfessional(@PathVariable UUID userId) {
        ProfessionalProfile profile = professionalProfileService.getActiveProfessional(userId);
        return ApiResponse.success(PublicProfessionalProfileResponse.from(
                profile, Instant.now().minus(onlineThreshold)));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProfessionalCardResponse>> listProfessionals(
            @PageableDefault(size = 12, sort = "userId") Pageable pageable,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String cityCode) {

        Page<ProfessionalProfile> profiles = professionalProfileService.getActiveProfessionals(pageable,
                normalise(categoryCode),
                normalise(cityCode));

        // Computed once, so every card on this page is judged against the same
        // instant. Calling Instant.now() per card would let the first and last
        // card disagree about "now".
        Instant onlineCutoff = Instant.now().minus(onlineThreshold);

        PageResponse<ProfessionalCardResponse> response = PageResponse.from(
                profiles,
                profile -> ProfessionalCardResponse.from(profile, onlineCutoff));

        return ApiResponse.success(response);
    }
}
