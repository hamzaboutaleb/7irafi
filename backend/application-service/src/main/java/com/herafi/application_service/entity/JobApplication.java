package com.herafi.application_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job_applications", uniqueConstraints = @UniqueConstraint(
        name = "uq_job_applications_job_applicant",
        columnNames = {"job_id", "applicant_id"}))
public class JobApplication {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "applicant_id", nullable = false, updatable = false)
    private UUID applicantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
