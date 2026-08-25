package com.herafi.job_service.dto;

import com.herafi.job_service.entity.JobStatus;

import java.math.BigDecimal;

import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.PreferredTime;

public record JobFilter(
        String skill,
        String city,
        JobStatus status,
        BigDecimal minBudget,
        BigDecimal maxBudget,
        EstimatedDuration duration,
        PreferredTime preferredTime) {
}