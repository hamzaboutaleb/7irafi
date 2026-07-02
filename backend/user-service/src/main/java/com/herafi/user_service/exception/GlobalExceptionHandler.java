package com.herafi.user_service.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.herafi.user_service.dto.ApiErrorResponse;
import com.herafi.user_service.dto.ApiErrorResponse.FieldValidationError;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(UserProfileNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("USER_PROFILE_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(UserProfileAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAlreadyExists(
            UserProfileAlreadyExistsException exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("USER_PROFILE_ALREADY_EXISTS", exception.getMessage()));
    }

    @ExceptionHandler({ProfileEmailAlreadyInUseException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleEmailConflict(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(
                        "PROFILE_EMAIL_ALREADY_IN_USE",
                        "Email already belongs to another profile"
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception) {
        List<FieldValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation(errors));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingIdentity(
            MissingRequestHeaderException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of(
                        "MISSING_USER_IDENTITY",
                        "Authenticated user identity is missing"
                ));
    }
}
