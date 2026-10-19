package com.herafi.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analytics_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsApplication {
    @Id
    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "job_skill", nullable = false, length = 40)
    private String jobSkill;

    @Column(name = "job_city", nullable = false, length = 40)
    private String jobCity;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
