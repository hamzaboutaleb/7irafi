package com.herafi.rating_service.exception;

public class JobNotCompletedException extends RatingException {

    public JobNotCompletedException() {
        super("JOB_NOT_COMPLETED", "Job must be completed before rating");
    }
}