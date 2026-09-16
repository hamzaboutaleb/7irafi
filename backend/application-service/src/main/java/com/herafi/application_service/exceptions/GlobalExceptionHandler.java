package com.herafi.application_service.exceptions;

import java.time.Instant;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplication(ApplicationException exception,
            HttpServletRequest request) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        var fields = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiErrorResponse.FieldError(field.getField(), field.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request has an invalid format", request, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingIdentity(MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Required identity header is missing", request, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handlePagination(HandlerMethodValidationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                "Page must be nonnegative and size must be between 1 and 20", request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, List<ApiErrorResponse.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(Instant.now(), status.value(),
                code, message, request.getRequestURI(), fields));
    }
}
