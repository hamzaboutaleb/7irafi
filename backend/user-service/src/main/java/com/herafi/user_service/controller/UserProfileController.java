package com.herafi.user_service.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.dto.UserProfileResponse;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser(
            @RequestHeader("X-User-Id") UUID userId) {
        UserProfile profile = userProfileService.get(userId);
        return ApiResponse.success(UserProfileResponse.from(profile));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateCurrentUser(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfile profile = userProfileService.update(userId, request);
        return ApiResponse.success(UserProfileResponse.from(profile));
    }

    @PostMapping("/me/heartbeat")
    public ResponseEntity<Void> recordHeartbeat(
            @RequestHeader("X-User-Id") UUID userId) {
        userProfileService.recordHeartbeat(userId);
        return ResponseEntity.status(204).body(null);
    }
}
