package com.herafi.analytics.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class ApplicationEvent {
    private UUID applicationId;
    private UUID jobId;
    private UUID applicantId;
    private String jobSkill;
    private String jobCity;
    private String status;
    private Instant timestamp;
}