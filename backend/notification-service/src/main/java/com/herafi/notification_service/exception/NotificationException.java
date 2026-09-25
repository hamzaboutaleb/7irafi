package com.herafi.notification_service.exception;

import org.springframework.http.HttpStatus;

/**
 * One exception type for the service's own failures, carrying the status and the
 * error code the client branches on.
 */
public class NotificationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public NotificationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
