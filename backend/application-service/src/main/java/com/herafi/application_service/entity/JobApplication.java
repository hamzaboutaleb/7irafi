package com.herafi.application_service.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.herafi.application_service.event.ApplicationAnalyticsEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
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

    // Nullable only for applications created before analytics metadata was retained.
    @Column(length = 40)
    private String jobSkill;

    @Column(length = 40)
    private String jobCity;

    /** Last analytics outcome; HTTP statuses continue to be derived from job-service. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationAnalyticsEvent.Status analyticsStatus = ApplicationAnalyticsEvent.Status.PENDING;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
