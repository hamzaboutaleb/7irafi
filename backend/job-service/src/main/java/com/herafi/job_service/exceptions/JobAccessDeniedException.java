package com.herafi.job_service.exceptions;

public class JobAccessDeniedException extends RuntimeException {

    public JobAccessDeniedException(String msg) {
        super(msg);
    }
}