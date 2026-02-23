package com.codewiki.dto;

/**
 * Categories for error classification.
 * Used to group errors by their nature for consistent handling.
 */
public enum ErrorCategory {
    /**
     * Validation errors: invalid URLs, oversized repositories, empty repositories
     */
    VALIDATION_ERROR,
    
    /**
     * Cloning errors: Git operation failures, network issues, authentication problems
     */
    CLONING_ERROR,
    
    /**
     * LLM errors: API failures, timeout, rate limiting, invalid responses
     */
    LLM_ERROR,
    
    /**
     * Database errors: Connection failures, constraint violations, query errors
     */
    DATABASE_ERROR,
    
    /**
     * System errors: Unexpected exceptions, resource exhaustion, configuration issues
     */
    SYSTEM_ERROR
}
