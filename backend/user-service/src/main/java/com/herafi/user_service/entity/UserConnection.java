package com.herafi.user_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_connections")
@IdClass(UserConnectionId.class)
public class UserConnection {

    @Id
    private UUID ownerId;

    @Id
    private UUID connectionId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

}
