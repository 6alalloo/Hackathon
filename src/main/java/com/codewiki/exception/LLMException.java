package com.codewiki.exception;

import lombok.Getter;

import java.util.List;

/**
 * Exception thrown for LLM API errors.
 * Includes error code and optional recovery suggestions.
 */
@Getter
public class LLMException extends RuntimeException {
    
    private final String errorCode;
    private final List<String> suggestions;
    
    public LLMException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = List.of();
    }
    
    public LLMException(String errorCode, String message, List<String> suggestions) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = suggestions;
    }
    
    public LLMException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestions = List.of();
    }
}
