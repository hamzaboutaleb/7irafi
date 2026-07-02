package com.herafi.auth_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.auth_service.entity.UserCredential;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByEmail(String email);
    
    Boolean existsByEmail(String email);

}

