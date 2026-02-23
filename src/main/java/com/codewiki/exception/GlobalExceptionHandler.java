package com.codewiki.exception;

import com.codewiki.dto.ErrorCategory;
import com.codewiki.dto.ErrorCode;
import com.codewiki.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Global exception handler for centralized error handling.
 * Catches all exceptions and converts them to standardized error responses.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle validation exceptions (invalid URLs, oversized repos, empty repos)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        log.info("Validation error: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .category(ErrorCategory.VALIDATION_ERROR)
                .timestamp(LocalDateTime.now())
                .suggestions(ex.getSuggestions())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle cloning exceptions (Git failures, network issues)
     */
    @ExceptionHandler(CloningException.class)
    public ResponseEntity<ErrorResponse> handleCloningException(
            CloningException ex, WebRequest request) {
        
        log.warn("Cloning error: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .category(ErrorCategory.CLONING_ERROR)
                .timestamp(LocalDateTime.now())
                .suggestions(ex.getSuggestions())
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
    }
    
    /**
     * Handle LLM API exceptions (API failures, timeouts, rate limits)
     */
    @ExceptionHandler(LLMException.class)
    public ResponseEntity<ErrorResponse> handleLLMException(
            LLMException ex, WebRequest request) {
        
        log.error("LLM error: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .category(ErrorCategory.LLM_ERROR)
                .timestamp(LocalDateTime.now())
                .suggestions(ex.getSuggestions())
                .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle database exceptions (connection failures, constraint violations)
     */
    @ExceptionHandler({DatabaseException.class, DataAccessException.class})
    public ResponseEntity<ErrorResponse> handleDatabaseException(
            Exception ex, WebRequest request) {
        
        log.error("Database error: {}", ex.getMessage(), ex);
        
        String errorCode = ErrorCode.DATABASE_ERROR;
        String message = "A database error occurred. Please try again later.";
        List<String> suggestions = List.of("Try again in a few moments", "Contact support if the issue persists");
        
        if (ex instanceof DatabaseException) {
            DatabaseException dbEx = (DatabaseException) ex;
            errorCode = dbEx.getErrorCode();
            message = dbEx.getMessage();
            suggestions = dbEx.getSuggestions().isEmpty() ? suggestions : dbEx.getSuggestions();
        }
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(errorCode)
                .message(message)
                .category(ErrorCategory.DATABASE_ERROR)
                .timestamp(LocalDateTime.now())
                .suggestions(suggestions)
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ErrorCode.INTERNAL_ERROR)
                .message("An unexpected error occurred. Please try again later.")
                .category(ErrorCategory.SYSTEM_ERROR)
                .timestamp(LocalDateTime.now())
                .suggestions(List.of("Try again in a few moments", "Contact support if the issue persists"))
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
