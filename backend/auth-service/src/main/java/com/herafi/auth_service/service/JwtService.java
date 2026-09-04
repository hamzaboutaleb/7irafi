package com.herafi.auth_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.herafi.auth_service.entity.UserCredential;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;
    private final String issuer;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${security.jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.issuer = issuer;
    }

    public String generateAccessToken(UserCredential credential) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(credential.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", credential.getEmail())
                .claim("role", credential.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
