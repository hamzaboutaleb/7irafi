package com.herafi.user_service.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.UpdateUserProfileRequest;
import com.herafi.user_service.dto.UserProfileResponse;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.AvatarService;
import com.herafi.user_service.service.UserProfileService;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final AvatarService avatarService;

    public UserProfileController(UserProfileService userProfileService, AvatarService avatarService) {
        this.userProfileService = userProfileService;
        this.avatarService = avatarService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser(
            @RequestHeader("X-User-Id") UUID userId) {
        UserProfile profile = userProfileService.get(userId);
        return ApiResponse.success(UserProfileResponse.from(profile, avatarService.urlFor(profile)));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateCurrentUser(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UserProfile profile = userProfileService.update(userId, request);
        return ApiResponse.success(UserProfileResponse.from(profile, avatarService.urlFor(profile)));
    }

    @PostMapping("/me/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatar(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("file") MultipartFile file) {
        UserProfile profile = avatarService.replace(userId, file);
        return ApiResponse.success(UserProfileResponse.from(profile, avatarService.urlFor(profile)));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteAvatar(@RequestHeader("X-User-Id") UUID userId) {
        avatarService.remove(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/heartbeat")
    public ResponseEntity<Void> recordHeartbeat(
            @RequestHeader("X-User-Id") UUID userId) {
        userProfileService.recordHeartbeat(userId);
        return ResponseEntity.status(204).body(null);
    }
}
