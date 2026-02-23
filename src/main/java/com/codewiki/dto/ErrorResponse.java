package com.codewiki.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response DTO for all API errors.
 * Provides consistent error structure across the application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    /**
     * Error code identifying the specific error type
     */
    private String code;
    
    /**
     * Human-readable error message
     */
    private String message;
    
    /**
     * Error category for classification
     */
    private ErrorCategory category;
    
    /**
     * Timestamp when the error occurred
     */
    private LocalDateTime timestamp;
    
    /**
     * Optional suggestions for error recovery
     */
    private List<String> suggestions;
}
