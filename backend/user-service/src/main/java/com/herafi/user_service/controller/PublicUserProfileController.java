package com.herafi.user_service.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.PublicUserProfileResponse;
import com.herafi.user_service.service.PublicUserProfileService;

@RestController
@RequestMapping("/users")
public class PublicUserProfileController {
    private final PublicUserProfileService profiles;

    public PublicUserProfileController(PublicUserProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<PublicUserProfileResponse> get(@PathVariable UUID userId) {
        return ApiResponse.success(profiles.get(userId));
    }
}
