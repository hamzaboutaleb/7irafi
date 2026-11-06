package com.herafi.user_service.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.CreateUserProfileRequest;
import com.herafi.user_service.dto.UserDisplayNameResponse;
import com.herafi.user_service.dto.UserProfileResponse;
import com.herafi.user_service.entity.UserProfile;
import com.herafi.user_service.service.UserProfileService;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserProfileService userProfileService;

    public InternalUserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/{userId}/exists")
    public ResponseEntity<Void> exists(@PathVariable UUID userId) {
        userProfileService.get(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns names for one batch of users, protected by the internal API key filter.
     * Missing profiles are omitted so one removed user does not break a review page.
     * The limit matches the rating service's maximum review page size.
     */
    @GetMapping("/names")
    public List<UserDisplayNameResponse> getDisplayNames(
            @RequestParam @Size(min = 1, max = 100) List<UUID> ids) {
        return userProfileService.getDisplayNames(ids);
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
