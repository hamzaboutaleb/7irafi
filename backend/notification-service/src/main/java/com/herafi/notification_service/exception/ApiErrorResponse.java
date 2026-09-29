package com.herafi.notification_service.exception;

import java.util.List;

/**
 * Same error envelope the rest of the project uses, and the same one
 * TrustedCallerFilter writes by hand when it rejects a request.
 */
public record ApiErrorResponse(
        boolean success,
        ErrorDetails error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorDetails(code, message, List.of()));
    }

    public static ApiErrorResponse validation(List<FieldValidationError> errors) {
        return new ApiErrorResponse(
                false,
                new ErrorDetails("VALIDATION_ERROR", "Validation failed", errors));
    }

    public record ErrorDetails(
            String code,
            String message,
            List<FieldValidationError> errors) {
    }

    public record FieldValidationError(
            String field,
            String message) {
    }
}
