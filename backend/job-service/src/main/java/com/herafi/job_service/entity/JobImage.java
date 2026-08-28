package com.herafi.job_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_images")
@Getter
@Setter
@NoArgsConstructor
public class JobImage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}