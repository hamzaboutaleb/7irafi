package com.herafi.job_service.exceptions;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message) {
        super(message);
    } 
    public JobNotFoundException(String message, UUID id) {
        super(message + id);
    }
}