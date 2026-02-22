package com.codewiki.model;

/**
 * Represents the result of a validation operation.
 * Contains success/failure status and error messages.
 */
public class ValidationResult {
    private final boolean success;
    private final String errorMessage;

    private ValidationResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    /**
     * Creates a failed validation result with an error message.
     */
    public static ValidationResult failure(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty");
        }
        return new ValidationResult(false, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return success ? "ValidationResult{success=true}" 
                       : "ValidationResult{success=false, errorMessage='" + errorMessage + "'}";
    }
}
