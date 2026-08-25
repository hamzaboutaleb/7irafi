package com.herafi.job_service.exceptions;

public class JobStateConflictException extends RuntimeException {

    public JobStateConflictException(String msg) {
        super(msg);
    }
}