package com.herafi.job_service.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_image_cleanup")
@Getter
@Setter
@NoArgsConstructor
public class JobImageCleanup {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private Instant nextAttemptAt;
}
