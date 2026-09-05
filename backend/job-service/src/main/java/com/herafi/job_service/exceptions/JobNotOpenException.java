package com.herafi.job_service.exceptions;

public class JobNotOpenException extends RuntimeException {

    public JobNotOpenException() {
        super("Job must be OPEN to be assigned");
    }
}
