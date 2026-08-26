package com.herafi.user_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.CreateProfessionalProfileRequest;
import com.herafi.user_service.dto.ProfessionalProfileResponse;
import com.herafi.user_service.dto.UpdateProfessionalProfileRequest;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.service.ProfessionalProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me/professional-profile")
public class ProfessionalProfileController {
    private final ProfessionalProfileService professionalProfileService;

    public ProfessionalProfileController(ProfessionalProfileService professionalProfileService) {
        this.professionalProfileService = professionalProfileService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfessionalProfileResponse>> create(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateProfessionalProfileRequest request) {
        ProfessionalProfile professionalProfile = professionalProfileService.create(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProfessionalProfileResponse.from(professionalProfile)));
    }

    @GetMapping
    public ApiResponse<ProfessionalProfileResponse> getCurrentProfessionalProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        ProfessionalProfile professionalProfile = professionalProfileService.get(userId);

        return ApiResponse.success(ProfessionalProfileResponse.from(professionalProfile));
    }

    @PutMapping
    public ApiResponse<ProfessionalProfileResponse> updateBiography(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfessionalProfileRequest request) {
        ProfessionalProfile professionalProfile = professionalProfileService.update(userId, request);

        return ApiResponse.success(ProfessionalProfileResponse.from(professionalProfile));
    }
}
