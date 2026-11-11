package com.herafi.rating_service.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.herafi.rating_service.dto.ApiErrorResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Two requests can both pass the duplicate check before either one saves.
     * The database rejects the second insert using this named unique index.
     * Handle that failure here because it may happen when the transaction commits,
     * after the service method has returned. Other database failures remain server errors.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_ratings_job_rater_ratee".equalsIgnoreCase(violation.getConstraintName())) {
                return handleRatingException(new DuplicateRatingException());
            }
            cause = cause.getCause();
        }
        return handleGeneric(ex);
    }

    @ExceptionHandler(RatingException.class)
    public ResponseEntity<ApiErrorResponse> handleRatingException(RatingException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "DUPLICATE_RATING" -> HttpStatus.CONFLICT;
            case "JOB_NOT_COMPLETED" -> HttpStatus.BAD_REQUEST;
            case "USER_NOT_FOUND", "JOB_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_RATING_PARTICIPANT" -> HttpStatus.FORBIDDEN;
            case "JOB_SERVICE_UNAVAILABLE", "USER_SERVICE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
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

    /** Broken JSON, incompatible field values, and an absent body are request errors. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_REQUEST_BODY", "Provide a valid JSON request body"));
    }

    /** The controller expects JSON; an unsupported body format is not a server failure. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Send JSON with Content-Type application/json"));
    }

    /** The gateway must supply the authenticated user ID before a rating can be created. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingIdentity(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("MISSING_USER_IDENTITY", "Authenticated user identity is missing"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        // Keep the useful failure details on the server, not in the browser's response.
        log.error("Unexpected error while processing a rating request", ex);
        return ResponseEntity.internalServerError()
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidParameters(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_REQUEST", "Invalid identifier or pagination parameters"));
    }
}
