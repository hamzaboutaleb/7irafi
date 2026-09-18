package com.herafi.user_service.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.user_service.dto.ApiResponse;
import com.herafi.user_service.dto.ProfessionalCardResponse;
import com.herafi.user_service.entity.ProfessionalProfile;
import com.herafi.user_service.service.UserConnectionService;

/**
 * The caller's own network — professionals they have saved.
 *
 * <p>The owner is always taken from the gateway-injected X-User-Id header, never
 * from the path or body. You can only ever read and change your own network.
 */
@RestController
@RequestMapping("/users/me/connections")
public class UserConnectionController {

    private final UserConnectionService userConnectionService;
    private final Duration onlineThreshold;

    public UserConnectionController(
            UserConnectionService userConnectionService,
            @Value("${users.presence.online-threshold}") Duration onlineThreshold) {
        this.userConnectionService = userConnectionService;
        this.onlineThreshold = onlineThreshold;
    }

    @GetMapping
    public ApiResponse<List<ProfessionalCardResponse>> listConnections(
            @RequestHeader("X-User-Id") UUID ownerId) {

        List<ProfessionalProfile> profiles = userConnectionService.getConnections(ownerId);

        // Computed once so every card in the response is judged against the same
        // instant, as on the directory.
        Instant onlineCutoff = Instant.now().minus(onlineThreshold);

        List<ProfessionalCardResponse> cards = profiles.stream()
                .map(profile -> ProfessionalCardResponse.from(profile, onlineCutoff))
                .toList();

        return ApiResponse.success(cards);
    }

    @PostMapping("/{targetId}")
    public ResponseEntity<Void> addConnection(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID targetId) {

        userConnectionService.addConnection(ownerId, targetId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> removeConnection(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID targetId) {

        userConnectionService.removeConnection(ownerId, targetId);

        return ResponseEntity.noContent().build();
    }
}
