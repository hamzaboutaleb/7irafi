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
@Table(name = "analytics_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsJob {
    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 40)
    private String skill;

    @Column(nullable = false, length = 40)
    private String city;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
