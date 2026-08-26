package com.herafi.user_service.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    boolean existsByEmail(String email);
}
