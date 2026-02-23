package com.codewiki.exception;

import lombok.Getter;

import java.util.List;

/**
 * Exception thrown for repository cloning errors.
 * Includes error code and optional recovery suggestions.
 */
@Getter
public class CloningException extends RuntimeException {
    
    private final String errorCode;
    private final List<String> suggestions;
    
    public CloningException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = List.of();
    }
    
    public CloningException(String errorCode, String message, List<String> suggestions) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = suggestions;
    }
    
    public CloningException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestions = List.of();
    }
}
