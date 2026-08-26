package com.herafi.job_service.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 40)
    private String skill;

    @Column(nullable = false, length = 40)
    private String city;

    @Column(length = 200)
    private String addressLine;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal budget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstimatedDuration estimatedDuration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PreferredTime preferredTime = PreferredTime.ANYTIME;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.OPEN;

    @Column
    private UUID assignedWorkerId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private boolean deleted = false;

    private Instant deletedAt;

    private UUID deletedBy;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}