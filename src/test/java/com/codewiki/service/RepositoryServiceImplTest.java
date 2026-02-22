package com.codewiki.service;

import com.codewiki.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RepositoryServiceImpl.
 */
class RepositoryServiceImplTest {
    
    private RepositoryService repositoryService;
    
    @BeforeEach
    void setUp() {
        repositoryService = new RepositoryServiceImpl();
    }
    
    @Test
    void testValidateRepositoryUrl_ValidUrl_ReturnsSuccess() {
        // Valid GitHub URL
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/owner/repo"
        );
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_ValidUrlWithHyphens_ReturnsSuccess() {
        // Valid URL with hyphens in owner and repo names
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/my-owner/my-repo"
        );
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_ValidUrlWithUnderscores_ReturnsSuccess() {
        // Valid URL with underscores
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/my_owner/my_repo"
        );
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_ValidUrlWithDots_ReturnsSuccess() {
        // Valid URL with dots in repo name
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/owner/repo.name"
        );
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_ValidUrlWithGitExtension_ReturnsSuccess() {
        // Valid URL with .git extension
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/owner/repo.git"
        );
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_ValidUrlWithWhitespace_ReturnsSuccess() {
        // Valid URL with leading/trailing whitespace
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "  https://github.com/owner/repo  "
        );
        
        assertTrue(result.isSuccess());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_NullUrl_ReturnsFailure() {
        ValidationResult result = repositoryService.validateRepositoryUrl(null);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("null or empty"));
    }
    
    @Test
    void testValidateRepositoryUrl_EmptyUrl_ReturnsFailure() {
        ValidationResult result = repositoryService.validateRepositoryUrl("");
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("null or empty"));
    }
    
    @Test
    void testValidateRepositoryUrl_WhitespaceOnlyUrl_ReturnsFailure() {
        ValidationResult result = repositoryService.validateRepositoryUrl("   ");
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("null or empty"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "http://github.com/owner/repo",           // HTTP instead of HTTPS
        "https://gitlab.com/owner/repo",          // GitLab instead of GitHub
        "https://github.com/owner",               // Missing repo name
        "https://github.com/owner/repo/extra",    // Extra path segments
        "github.com/owner/repo",                  // Missing protocol
        "https://github.com/",                    // Missing owner and repo
        "https://github.com/owner/",              // Missing repo name
        "https://www.github.com/owner/repo",      // www subdomain
        "https://api.github.com/repos/owner/repo" // API URL
    })
    void testValidateRepositoryUrl_InvalidUrls_ReturnsFailure(String invalidUrl) {
        ValidationResult result = repositoryService.validateRepositoryUrl(invalidUrl);
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Invalid GitHub repository URL format"));
    }
    
    @Test
    void testValidateRepositoryUrl_UrlWithSpecialCharacters_ReturnsFailure() {
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/owner/repo@123"
        );
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testValidateRepositoryUrl_UrlWithSpaces_ReturnsFailure() {
        ValidationResult result = repositoryService.validateRepositoryUrl(
            "https://github.com/my owner/my repo"
        );
        
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
}
