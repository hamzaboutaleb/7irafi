package com.herafi.rating_service.exception;

public class RatingException extends RuntimeException {

    private final String errorCode;

    public RatingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}