package com.herafi.analytics.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class JobEvent {
    private UUID jobId;
    private UUID clientId;
    private String status;
    private String skill;
    private String city;
    private Instant timestamp;
}