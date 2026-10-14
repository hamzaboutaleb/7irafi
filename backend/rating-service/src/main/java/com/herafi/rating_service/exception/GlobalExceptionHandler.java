package com.herafi.rating_service.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.herafi.rating_service.dto.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RatingException.class)
    public ResponseEntity<ApiErrorResponse> handleRatingException(RatingException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "DUPLICATE_RATING" -> HttpStatus.CONFLICT;
            case "JOB_NOT_COMPLETED" -> HttpStatus.BAD_REQUEST;
            case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "JOB_SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorResponse.FieldValidationError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}