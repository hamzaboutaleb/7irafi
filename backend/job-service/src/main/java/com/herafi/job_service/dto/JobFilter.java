package com.herafi.job_service.dto;

import com.herafi.job_service.entity.JobStatus;

import java.math.BigDecimal;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import com.herafi.job_service.entity.EstimatedDuration;
import com.herafi.job_service.entity.PreferredTime;

public record JobFilter(
        String skill,
        String city,
        JobStatus status,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal minBudget,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2)
        BigDecimal maxBudget,
        EstimatedDuration duration,
        PreferredTime preferredTime) {
    @AssertTrue(message = "minBudget must be less than or equal to maxBudget")
    public boolean isBudgetRangeValid() {
        return minBudget == null || maxBudget == null || minBudget.compareTo(maxBudget) <= 0;
    }
}
