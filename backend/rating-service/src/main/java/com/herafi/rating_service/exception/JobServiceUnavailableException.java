package com.herafi.rating_service.exception;

public class JobServiceUnavailableException extends RatingException {

    public JobServiceUnavailableException() {
        super("JOB_SERVICE_UNAVAILABLE", "Job service is unavailable");
    }
}