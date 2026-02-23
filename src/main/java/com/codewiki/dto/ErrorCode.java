package com.codewiki.dto;

/**
 * Standard error codes for all error types in the application.
 * Each code uniquely identifies a specific error condition.
 */
public class ErrorCode {
    
    // Validation error codes
    public static final String INVALID_REPOSITORY_URL = "INVALID_REPOSITORY_URL";
    public static final String REPOSITORY_TOO_LARGE = "REPOSITORY_TOO_LARGE";
    public static final String NO_CODE_FILES = "NO_CODE_FILES";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    
    // Cloning error codes
    public static final String CLONING_FAILED = "CLONING_FAILED";
    public static final String REPOSITORY_NOT_FOUND = "REPOSITORY_NOT_FOUND";
    public static final String REPOSITORY_ACCESS_DENIED = "REPOSITORY_ACCESS_DENIED";
    
    // LLM error codes
    public static final String LLM_API_ERROR = "LLM_API_ERROR";
    public static final String LLM_TIMEOUT = "LLM_TIMEOUT";
    public static final String LLM_RATE_LIMIT = "LLM_RATE_LIMIT";
    public static final String LLM_INVALID_RESPONSE = "LLM_INVALID_RESPONSE";
    
    // Database error codes
    public static final String DATABASE_ERROR = "DATABASE_ERROR";
    public static final String WIKI_NOT_FOUND = "WIKI_NOT_FOUND";
    public static final String DUPLICATE_WIKI = "DUPLICATE_WIKI";
    
    // System error codes
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED";
    public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
    
    private ErrorCode() {
        // Utility class, prevent instantiation
    }
}
