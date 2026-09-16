package com.herafi.application_service.exceptions;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApplicationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
