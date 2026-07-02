package com.herafi.user_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UserProfileResponse;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserProfileService userProfileService;

    public InternalUserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> create(
            @Valid @RequestBody CreateUserProfileRequest request) {
        UserProfile profile = userProfileService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserProfileResponse.from(profile)));
    }
}
