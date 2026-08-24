package com.herafi.job_service.exceptions;

import com.herafi.job_service.entity.JobStatus;

public class InvalidJobStateException extends RuntimeException {

    public InvalidJobStateException(JobStatus status) {
        super("Job cannot be completed from status: " + status);
    }
}