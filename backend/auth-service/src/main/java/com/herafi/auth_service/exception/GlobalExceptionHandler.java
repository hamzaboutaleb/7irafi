package com.herafi.auth_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.herafi.auth_service.dto.ApiErrorResponse;
import com.herafi.auth_service.dto.ApiErrorResponse.FieldValidationError;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {
    

    @ExceptionHandler({EmailAlreadyInUseException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleEmailConflict(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("EMAIL_ALREADY_IN_USE", "Email already in use"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("INVALID_CREDENTIALS", exception.getMessage()));
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUserServiceUnavailable(
            UserServiceUnavailableException exception) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("USER_SERVICE_UNAVAILABLE", exception.getMessage()));
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
}
