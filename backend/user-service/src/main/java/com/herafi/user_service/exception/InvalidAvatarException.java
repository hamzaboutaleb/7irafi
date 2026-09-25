package com.herafi.user_service.exception;

/**
 * The uploaded file is not something we accept as an avatar. Carries the error
 * code so the frontend can say exactly what was wrong.
 */
public class InvalidAvatarException extends RuntimeException {

    private final String code;

    public InvalidAvatarException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
