package com.herafi.auth_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.herafi.auth_service.dto.AuthResponse;
import com.herafi.auth_service.dto.LoginRequest;
import com.herafi.auth_service.dto.RegisterRequest;
import com.herafi.auth_service.entity.UserCredential;
import com.herafi.auth_service.service.AuthenticationService;
import com.herafi.auth_service.service.JwtService;
import com.herafi.auth_service.service.RegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    public AuthController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            JwtService jwtService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        UserCredential credential = registrationService.register(request);
        String accessToken = jwtService.generateAccessToken(credential);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AuthResponse.from(
                        credential,
                        accessToken,
                        jwtService.getAccessTokenTtlSeconds()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserCredential credential = authenticationService.authenticate(request);
        String accessToken = jwtService.generateAccessToken(credential);

        return AuthResponse.from(
                credential,
                accessToken,
                jwtService.getAccessTokenTtlSeconds());
    }
}
