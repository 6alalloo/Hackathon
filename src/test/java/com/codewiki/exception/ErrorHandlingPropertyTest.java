package com.codewiki.exception;

import com.codewiki.dto.ErrorCategory;
import com.codewiki.dto.ErrorCode;
import com.codewiki.dto.ErrorResponse;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for global error handling without Spring test context coupling.
 */
class ErrorHandlingPropertyTest {

    private GlobalExceptionHandler exceptionHandler;
    private WebRequest webRequest;

    @BeforeTry
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        webRequest = Mockito.mock(WebRequest.class);
    }

    @Property(tries = 100)
    void cloningFailureHandling(
            @ForAll @NotBlank String errorMessage,
            @ForAll @NotBlank String repositoryUrl) {
        CloningException exception = new CloningException(
                ErrorCode.CLONING_FAILED,
                errorMessage,
                List.of("Check your internet connection", "Try again later")
        );

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleCloningException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.CLONING_FAILED);
        assertThat(response.getBody().getCategory()).isEqualTo(ErrorCategory.CLONING_ERROR);
    }

    @Property(tries = 100)
    void emptyRepositoryRejection(@ForAll @NotBlank String repositoryPath) {
        ValidationException exception = new ValidationException(
                ErrorCode.NO_CODE_FILES,
                "No code files detected in repository",
                List.of("Ensure the repository contains source code files")
        );

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.NO_CODE_FILES);
        assertThat(response.getBody().getCategory()).isEqualTo(ErrorCategory.VALIDATION_ERROR);
    }

    @Property(tries = 100)
    void errorLoggingCompleteness(
            @ForAll @NotBlank String errorCode,
            @ForAll @NotBlank String errorMessage,
            @ForAll("errorCategories") ErrorCategory category) {
        Exception exception = createExceptionForCategory(category, errorCode, errorMessage);
        ResponseEntity<ErrorResponse> response = handleExceptionByCategory(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isNotBlank();
        assertThat(response.getBody().getMessage()).isNotBlank();
        assertThat(response.getBody().getCategory()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Property(tries = 100)
    void userFriendlyErrorMessages(
            @ForAll @NotBlank String errorMessage,
            @ForAll @Size(min = 1, max = 5) List<@NotBlank String> suggestions) {
        ValidationException exception = new ValidationException(
                ErrorCode.INVALID_REPOSITORY_URL,
                errorMessage,
                suggestions
        );

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(exception, webRequest);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
        assertThat(response.getBody().getSuggestions()).containsExactlyElementsOf(suggestions);
    }

    @Property(tries = 100)
    void systemResilience(
            @ForAll @NotBlank String firstErrorMessage,
            @ForAll @NotBlank String secondErrorMessage) {
        ValidationException firstException = new ValidationException(
                ErrorCode.INVALID_REPOSITORY_URL,
                firstErrorMessage
        );
        ValidationException secondException = new ValidationException(
                ErrorCode.REPOSITORY_TOO_LARGE,
                secondErrorMessage
        );

        ResponseEntity<ErrorResponse> firstResponse =
                exceptionHandler.handleValidationException(firstException, webRequest);
        ResponseEntity<ErrorResponse> secondResponse =
                exceptionHandler.handleValidationException(secondException, webRequest);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
    }

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
        if (exception instanceof ValidationException validationException) {
            return exceptionHandler.handleValidationException(validationException, webRequest);
        }
        if (exception instanceof CloningException cloningException) {
            return exceptionHandler.handleCloningException(cloningException, webRequest);
        }
        if (exception instanceof LLMException llmException) {
            return exceptionHandler.handleLLMException(llmException, webRequest);
        }
        if (exception instanceof DatabaseException) {
            return exceptionHandler.handleDatabaseException(exception, webRequest);
        }
        return exceptionHandler.handleGenericException(exception, webRequest);
    }
}
