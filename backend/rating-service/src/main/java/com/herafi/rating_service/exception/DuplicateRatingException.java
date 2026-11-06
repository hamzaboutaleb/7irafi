package com.herafi.rating_service.exception;

public class DuplicateRatingException extends RatingException {

    public DuplicateRatingException() {
        super("DUPLICATE_RATING", "Rating already exists for this job and rater/ratee combination");
    }
}