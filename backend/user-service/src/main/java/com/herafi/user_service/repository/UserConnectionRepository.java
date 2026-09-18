package com.herafi.user_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.herafi.user_service.entity.UserConnection;
import com.herafi.user_service.entity.UserConnectionId;

public interface UserConnectionRepository extends JpaRepository<UserConnection, UserConnectionId> {

    List<UserConnection> findByOwnerId(UUID ownerId);

    boolean existsByOwnerIdAndConnectionId(UUID ownerId, UUID connectionId);

    long deleteByOwnerIdAndConnectionId(UUID ownerId, UUID connectionId);
}
