package com.herafi.job_service.dto;

public record UpdateJobRequest(
        String title,
        String description,
        String location
) {}