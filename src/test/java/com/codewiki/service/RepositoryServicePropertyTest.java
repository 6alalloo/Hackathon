package com.codewiki.service;

import com.codewiki.model.ValidationResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for RepositoryService using jqwik.
 */
class RepositoryServicePropertyTest {
    
    private final RepositoryService repositoryService = new RepositoryServiceImpl();
    
    /**
     * Feature: codewiki-generator, Property 1: URL Validation Correctness
     * For any submitted URL, the Repository_Validator should correctly identify
     * whether it is a valid public GitHub repository URL (format: https://github.com/{owner}/{repo})
     * and reject invalid URLs with appropriate error messages.
     * 
     * Validates: Requirements 1.2, 1.3
     */
    @Property(tries = 100)
    void urlValidationCorrectness_ValidGitHubUrls_ReturnsSuccess(
        @ForAll("validGitHubUrls") String url
    ) {
        ValidationResult result = repositoryService.validateRepositoryUrl(url);
        
        assertTrue(result.isSuccess(), 
            "Valid GitHub URL should pass validation: " + url);
        assertNull(result.getErrorMessage(), 
            "Successful validation should have no error message");
    }
    
    /**
     * Feature: codewiki-generator, Property 1: URL Validation Correctness
     * Invalid URLs should be rejected with appropriate error messages.
     * 
     * Validates: Requirements 1.2, 1.3
     */
    @Property(tries = 100)
    void urlValidationCorrectness_InvalidUrls_ReturnsFailure(
        @ForAll("invalidGitHubUrls") String url
    ) {
        ValidationResult result = repositoryService.validateRepositoryUrl(url);
        
        assertFalse(result.isSuccess(), 
            "Invalid URL should fail validation: " + url);
        assertNotNull(result.getErrorMessage(), 
            "Failed validation should have an error message");
        assertFalse(result.getErrorMessage().trim().isEmpty(), 
            "Error message should not be empty");
    }
    
    /**
     * Feature: codewiki-generator, Property 1: URL Validation Correctness
     * Null or empty URLs should be rejected with appropriate error messages.
     * 
     * Validates: Requirements 1.2, 1.3
     */
    @Property(tries = 50)
    void urlValidationCorrectness_NullOrEmptyUrls_ReturnsFailure(
        @ForAll("nullOrEmptyStrings") String url
    ) {
        ValidationResult result = repositoryService.validateRepositoryUrl(url);
        
        assertFalse(result.isSuccess(), 
            "Null or empty URL should fail validation");
        assertNotNull(result.getErrorMessage(), 
            "Failed validation should have an error message");
        assertTrue(result.getErrorMessage().contains("null or empty"), 
            "Error message should mention null or empty");
    }
    
    /**
     * Feature: codewiki-generator, Property 1: URL Validation Correctness
     * URLs with whitespace should be handled correctly (trimmed and validated).
     * 
     * Validates: Requirements 1.2, 1.3
     */
    @Property(tries = 50)
    void urlValidationCorrectness_UrlsWithWhitespace_HandledCorrectly(
        @ForAll("validGitHubUrls") String validUrl,
        @ForAll("whitespace") String leadingWhitespace,
        @ForAll("whitespace") String trailingWhitespace
    ) {
        String urlWithWhitespace = leadingWhitespace + validUrl + trailingWhitespace;
        ValidationResult result = repositoryService.validateRepositoryUrl(urlWithWhitespace);
        
        assertTrue(result.isSuccess(), 
            "Valid GitHub URL with whitespace should pass validation after trimming: " + urlWithWhitespace);
        assertNull(result.getErrorMessage());
    }
    
    // ========== Arbitraries (Generators) ==========
    
    /**
     * Generates valid GitHub repository URLs.
     * Format: https://github.com/{owner}/{repo}
     */
    @Provide
    Arbitrary<String> validGitHubUrls() {
        Arbitrary<String> owner = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-', '_')
            .ofMinLength(1)
            .ofMaxLength(39); // GitHub username max length
        
        Arbitrary<String> repo = Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .withChars('-', '_', '.')
            .ofMinLength(1)
            .ofMaxLength(100); // GitHub repo name max length
        
        Arbitrary<String> gitExtension = Arbitraries.of("", ".git");
        
        return Combinators.combine(owner, repo, gitExtension)
            .as((o, r, ext) -> "https://github.com/" + o + "/" + r + ext);
    }
    
    /**
     * Generates invalid GitHub repository URLs.
     * Includes various invalid patterns.
     */
    @Provide
    Arbitrary<String> invalidGitHubUrls() {
        return Arbitraries.oneOf(
            // HTTP instead of HTTPS
            validGitHubUrls().map(url -> url.replace("https://", "http://")),
            
            // Wrong domain
            validGitHubUrls().map(url -> url.replace("github.com", "gitlab.com")),
            validGitHubUrls().map(url -> url.replace("github.com", "bitbucket.org")),
            
            // www subdomain
            validGitHubUrls().map(url -> url.replace("https://", "https://www.")),
            
            // API URL
            validGitHubUrls().map(url -> url.replace("github.com/", "api.github.com/repos/")),
            
            // Missing protocol
            validGitHubUrls().map(url -> url.replace("https://", "")),
            
            // Missing owner
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(100)
                .map(repo -> "https://github.com/" + repo),
            
            // Missing repo
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(39)
                .map(owner -> "https://github.com/" + owner + "/"),
            
            // Extra path segments
            validGitHubUrls().map(url -> url + "/extra/path"),
            
            // Special characters in owner/repo
            Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars('@', '#', '$', '%', '&', '*', ' ')
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(name -> "https://github.com/" + name + "/" + name),
            
            // Just the domain
            Arbitraries.just("https://github.com/"),
            Arbitraries.just("https://github.com"),
            
            // Random invalid URLs
            Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('/', ':', '.', '-')
                .ofMinLength(5)
                .ofMaxLength(100)
                .filter(s -> !s.matches("^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+(?:\\.git)?$"))
        );
    }
    
    /**
     * Generates null or empty strings.
     */
    @Provide
    Arbitrary<String> nullOrEmptyStrings() {
        return Arbitraries.oneOf(
            Arbitraries.just((String) null),
            Arbitraries.just(""),
            Arbitraries.strings().whitespace().ofMinLength(1).ofMaxLength(10)
        );
    }
    
    /**
     * Generates whitespace strings.
     */
    @Provide
    Arbitrary<String> whitespace() {
        return Arbitraries.strings()
            .whitespace()
            .ofMinLength(0)
            .ofMaxLength(5);
    }
}
