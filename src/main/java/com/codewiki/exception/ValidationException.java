package com.codewiki.exception;

import lombok.Getter;

import java.util.List;

/**
 * Exception thrown for validation errors.
 * Includes error code and optional recovery suggestions.
 */
@Getter
public class ValidationException extends RuntimeException {
    
    private final String errorCode;
    private final List<String> suggestions;
    
    public ValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = List.of();
    }
    
    public ValidationException(String errorCode, String message, List<String> suggestions) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = suggestions;
    }
    
    public ValidationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestions = List.of();
    }
}
