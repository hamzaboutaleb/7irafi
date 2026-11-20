package com.herafi.auth_service.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.auth_service.dto.AuthResponse;
import com.herafi.auth_service.dto.LoginRequest;
import com.herafi.auth_service.dto.RefreshTokenRequest;
import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.service.AuthenticationService;
import com.herafi.auth_service.service.JwtService;
import com.herafi.auth_service.service.RefreshTokenService;
import com.herafi.auth_service.service.RegistrationService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserCredential credential = registrationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tokensFor(credential, refreshTokenService.create(credential.getId())));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserCredential credential = authenticationService.authenticate(request);

        return tokensFor(credential, refreshTokenService.create(credential.getId()));
    }

    // access token expired: trade the refresh token for a new pair (the old one stops working)
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());

        return tokensFor(rotation.user(), rotation.refreshToken());
    }

    // the refresh token stops working; the access token simply expires on its own
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());

        return ResponseEntity.noContent().build();
    }

    // a new access token plus the given refresh token
    private AuthResponse tokensFor(UserCredential credential, String refreshToken) {
        return AuthResponse.from(
                credential,
                jwtService.generateAccessToken(credential),
                jwtService.getAccessTokenTtlSeconds(),
                refreshToken);
    }
}
