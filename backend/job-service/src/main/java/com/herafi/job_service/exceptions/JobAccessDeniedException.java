package com.herafi.job_service.exceptions;

public class JobAccessDeniedException extends RuntimeException {

    public JobAccessDeniedException() {
        super("You are not the owner of this job");
    }
}