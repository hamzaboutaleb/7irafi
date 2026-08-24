package com.herafi.job_service.exceptions;

public class JobStateConflictException extends RuntimeException {

    public JobStateConflictException() {
        super("Job can only be updated when OPEN");
    }
}