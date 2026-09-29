package com.herafi.user_service.exception;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

        @ExceptionHandler({ ProfileEmailAlreadyInUseException.class, DataIntegrityViolationException.class })
        public ResponseEntity<ApiErrorResponse> handleEmailConflict(Exception exception) {
                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(ApiErrorResponse.of(
                                                "PROFILE_EMAIL_ALREADY_IN_USE",
                                                "Email already belongs to another profile"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleValidation(
                        MethodArgumentNotValidException exception) {
                List<FieldValidationError> errors = exception.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> new FieldValidationError(
                                                error.getField(),
                                                error.getDefaultMessage()))
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
                                                "Authenticated user identity is missing"));
        }

        @ExceptionHandler(InvalidAvatarException.class)
        public ResponseEntity<ApiErrorResponse> handleInvalidAvatar(InvalidAvatarException exception) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
        }

        // Spring rejects an oversized upload before the controller runs, so the
        // size rule has to be answered here as well as in AvatarService.
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiErrorResponse.of("AVATAR_TOO_LARGE", "The image is too large"));
        }

        @ExceptionHandler(ProfessionalProfileNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleNotFound(ProfessionalProfileNotFoundException exception) {
                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiErrorResponse.of("PROFESSIONAL_PROFILE_NOT_FOUND", exception.getMessage()));
        }

        @ExceptionHandler(SelfConnectionException.class)
        public ResponseEntity<ApiErrorResponse> handleSelfConnection(
                        SelfConnectionException exception) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiErrorResponse.of("SELF_CONNECTION_NOT_ALLOWED",
                                                exception.getMessage()));
        }

        @ExceptionHandler(NotAProfessionalException.class)
        public ResponseEntity<ApiErrorResponse> handleNotAProfessional(
                        NotAProfessionalException exception) {
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiErrorResponse.of("NOT_A_PROFESSIONAL", exception.getMessage()));
        }

        @ExceptionHandler(ConnectionNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleConnectionNotFound(
                        ConnectionNotFoundException exception) {
                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiErrorResponse.of("CONNECTION_NOT_FOUND", exception.getMessage()));
        }

        @ExceptionHandler(ProfessionalProfileAlreadyExistsException.class)
        public ResponseEntity<ApiErrorResponse> handleAlreadyExists(
                        ProfessionalProfileAlreadyExistsException exception) {
                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(ApiErrorResponse.of("PROFESSIONAL_PROFILE_ALREADY_EXISTS",
                                                exception.getMessage()));
        }

        @ExceptionHandler({ InvalidCategoryException.class, InvalidCityException.class })
        public ResponseEntity<ApiErrorResponse> handleInvalidCatalogCode(RuntimeException exception) {
                return ResponseEntity
                                .badRequest()
                                .body(ApiErrorResponse.of("INVALID_CATALOG_CODE", exception.getMessage()));
        }

}
