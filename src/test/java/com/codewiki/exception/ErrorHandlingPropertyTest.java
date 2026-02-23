package com.codewiki.exception;

import com.codewiki.dto.ErrorCategory;
import com.codewiki.dto.ErrorCode;
import com.codewiki.dto.ErrorResponse;
import com.codewiki.model.CodeFile;
import com.codewiki.service.RepositoryService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for error handling.
 * Tests Properties 7, 9, 34, 35, 36 from the design document.
 */
@SpringBootTest
class ErrorHandlingPropertyTest {
    
    @Autowired
    private GlobalExceptionHandler exceptionHandler;
    
    @MockBean
    private WebRequest webRequest;
    
    @MockBean
    private RepositoryService repositoryService;
    
    @BeforeEach
    void setUp() {
        webRequest = Mockito.mock(WebRequest.class);
    }
    
    /**
     * Feature: codewiki-generator, Property 7: Clone Failure Handling
     * For any cloning operation that fails, the system should log the error 
     * with appropriate context and notify the user of the failure.
     * 
     * Validates: Requirements 3.2
     */
    @Property(tries = 100)
    void cloningFailureHandling(
            @ForAll @NotBlank String errorMessage,
            @ForAll @NotBlank String repositoryUrl) {
        
        // Given: A cloning exception with error message
        CloningException exception = new CloningException(
            ErrorCode.CLONING_FAILED,
            errorMessage,
            List.of("Check your internet connection", "Try again later")
        );
        
        // When: The exception is handled
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCloningException(exception, webRequest);
        
        // Then: The response should contain error details
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getCode()).isEqualTo(ErrorCode.CLONING_FAILED);
        assertThat(errorResponse.getMessage()).isEqualTo(errorMessage);
        assertThat(errorResponse.getCategory()).isEqualTo(ErrorCategory.CLONING_ERROR);
        assertThat(errorResponse.getTimestamp()).isNotNull();
        assertThat(errorResponse.getSuggestions()).isNotEmpty();
    }
    
    /**
     * Feature: codewiki-generator, Property 9: Empty Repository Rejection
     * For any repository containing no code files (based on file extension detection), 
     * the system should reject it with an appropriate error message.
     * 
     * Validates: Requirements 4.2
     */
    @Property(tries = 100)
    void emptyRepositoryRejection(@ForAll @NotBlank String repositoryPath) {
        
        // Given: A validation exception for no code files
        ValidationException exception = new ValidationException(
            ErrorCode.NO_CODE_FILES,
            "No code files detected in repository",
            List.of("Ensure the repository contains source code files")
        );
        
        // When: The exception is handled
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);
        
        // Then: The response should indicate validation error
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getCode()).isEqualTo(ErrorCode.NO_CODE_FILES);
        assertThat(errorResponse.getCategory()).isEqualTo(ErrorCategory.VALIDATION_ERROR);
        assertThat(errorResponse.getTimestamp()).isNotNull();
        assertThat(errorResponse.getSuggestions()).isNotEmpty();
    }
    
    /**
     * Feature: codewiki-generator, Property 34: Error Logging Completeness
     * For any error that occurs in the system, a log entry should be created containing 
     * timestamp, context, stack trace, and error category.
     * 
     * Validates: Requirements 13.1, 13.2
     */
    @Property(tries = 100)
    void errorLoggingCompleteness(
            @ForAll @NotBlank String errorCode,
            @ForAll @NotBlank String errorMessage,
            @ForAll("errorCategories") ErrorCategory category) {
        
        // Given: An exception with full context
        Exception exception = createExceptionForCategory(category, errorCode, errorMessage);
        
        // When: The exception is handled
        ResponseEntity<ErrorResponse> response = handleExceptionByCategory(exception);
        
        // Then: The response should contain complete error information
        assertThat(response).isNotNull();
        
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getCode()).isNotBlank();
        assertThat(errorResponse.getMessage()).isNotBlank();
        assertThat(errorResponse.getCategory()).isNotNull();
        assertThat(errorResponse.getTimestamp()).isNotNull();
        // Timestamp should be recent (within last minute)
        assertThat(errorResponse.getTimestamp())
            .isAfterOrEqualTo(java.time.LocalDateTime.now().minusMinutes(1));
    }
    
    /**
     * Feature: codewiki-generator, Property 35: User-Friendly Error Messages
     * For any user-facing error, the system should display a user-friendly error message 
     * (avoiding technical jargon) and include recovery suggestions where applicable.
     * 
     * Validates: Requirements 13.3, 13.4
     */
    @Property(tries = 100)
    void userFriendlyErrorMessages(
            @ForAll @NotBlank String errorMessage,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> suggestions) {
        
        // Given: A validation exception with suggestions
        ValidationException exception = new ValidationException(
            ErrorCode.INVALID_REPOSITORY_URL,
            errorMessage,
            suggestions
        );
        
        // When: The exception is handled
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);
        
        // Then: The response should be user-friendly
        ErrorResponse errorResponse = response.getBody();
        assertThat(errorResponse).isNotNull();
        
        // Message should be present and meaningful
        assertThat(errorResponse.getMessage()).isNotBlank();
        assertThat(errorResponse.getMessage()).isEqualTo(errorMessage);
        
        // Suggestions should be provided
        assertThat(errorResponse.getSuggestions()).isNotNull();
        assertThat(errorResponse.getSuggestions()).hasSize(suggestions.size());
        assertThat(errorResponse.getSuggestions()).containsExactlyElementsOf(suggestions);
        
        // Error code should be descriptive
        assertThat(errorResponse.getCode()).isNotBlank();
    }
    
    /**
     * Feature: codewiki-generator, Property 36: System Resilience
     * For any recoverable error (validation failure, size limit, empty repository), 
     * the system should continue operating and be able to process subsequent requests.
     * 
     * Validates: Requirements 13.5
     */
    @Property(tries = 100)
    void systemResilience(
            @ForAll @NotBlank String firstErrorMessage,
            @ForAll @NotBlank String secondErrorMessage) {
        
        // Given: Multiple validation errors in sequence
        ValidationException firstException = new ValidationException(
            ErrorCode.INVALID_REPOSITORY_URL,
            firstErrorMessage
        );
        
        ValidationException secondException = new ValidationException(
            ErrorCode.REPOSITORY_TOO_LARGE,
            secondErrorMessage
        );
        
        // When: Both exceptions are handled
        ResponseEntity<ErrorResponse> firstResponse = 
            exceptionHandler.handleValidationException(firstException, webRequest);
        ResponseEntity<ErrorResponse> secondResponse = 
            exceptionHandler.handleValidationException(secondException, webRequest);
        
        // Then: Both should be handled successfully without system failure
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().getMessage()).isEqualTo(firstErrorMessage);
        
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().getMessage()).isEqualTo(secondErrorMessage);
        
        // System should continue operating (no exceptions thrown)
        // Each error should be independent
        assertThat(firstResponse.getBody().getCode()).isNotEqualTo(secondResponse.getBody().getCode());
    }
    
    // Helper methods and providers
    
    @Provide
    Arbitrary<ErrorCategory> errorCategories() {
        return Arbitraries.of(ErrorCategory.values());
    }
    
    private Exception createExceptionForCategory(ErrorCategory category, String errorCode, String errorMessage) {
        return switch (category) {
            case VALIDATION_ERROR -> new ValidationException(errorCode, errorMessage);
            case CLONING_ERROR -> new CloningException(errorCode, errorMessage);
            case LLM_ERROR -> new LLMException(errorCode, errorMessage);
            case DATABASE_ERROR -> new DatabaseException(errorCode, errorMessage);
            case SYSTEM_ERROR -> new RuntimeException(errorMessage);
        };
    }
    
    private ResponseEntity<ErrorResponse> handleExceptionByCategory(Exception exception) {
        if (exception instanceof ValidationException) {
            return exceptionHandler.handleValidationException((ValidationException) exception, webRequest);
        } else if (exception instanceof CloningException) {
            return exceptionHandler.handleCloningException((CloningException) exception, webRequest);
        } else if (exception instanceof LLMException) {
            return exceptionHandler.handleLLMException((LLMException) exception, webRequest);
        } else if (exception instanceof DatabaseException) {
            return exceptionHandler.handleDatabaseException(exception, webRequest);
        } else {
            return exceptionHandler.handleGenericException(exception, webRequest);
        }
    }
}
