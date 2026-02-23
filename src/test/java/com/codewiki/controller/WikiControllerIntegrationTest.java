package com.codewiki.controller;

import com.codewiki.dto.WikiSubmissionRequest;
import com.codewiki.dto.WikiSubmissionResponse;
import com.codewiki.model.*;
import com.codewiki.repository.GenerationStatusRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.service.RateLimiterService;
import com.codewiki.service.RepositoryService;
import com.codewiki.service.WikiGeneratorService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WikiController.
 * Tests end-to-end flows with mocked external dependencies.
 */
class WikiControllerIntegrationTest {
    
    private WikiController controller;
    private WikiRepository wikiRepository;
    private GenerationStatusRepository generationStatusRepository;
    private RepositoryService repositoryService;
    private WikiGeneratorService wikiGeneratorService;
    private RateLimiterService rateLimiterService;
    
    @BeforeEach
    void setUp() {
        wikiRepository = mock(WikiRepository.class);
        generationStatusRepository = mock(GenerationStatusRepository.class);
        repositoryService = mock(RepositoryService.class);
        wikiGeneratorService = mock(WikiGeneratorService.class);
        rateLimiterService = mock(RateLimiterService.class);
        
        controller = new WikiController();
        controller.wikiRepository = wikiRepository;
        controller.generationStatusRepository = generationStatusRepository;
        controller.repositoryService = repositoryService;
        controller.wikiGeneratorService = wikiGeneratorService;
        controller.rateLimiterService = rateLimiterService;
    }
    
    /**
     * Test end-to-end: Submit URL → Generate → Retrieve
     * Validates the complete wiki generation workflow.
     */
    @Test
    void testEndToEndWikiGeneration() throws GitAPIException {
        // Arrange
        String repoUrl = "https://github.com/test/repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);
        
        // Mock validation
        when(repositoryService.validateRepositoryUrl(repoUrl))
                .thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl))
                .thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot())
                .thenReturn(true);
        when(repositoryService.getRepositorySize(repoUrl))
                .thenReturn(5_000_000L);
        
        // Mock wiki creation
        Wiki createdWiki = new Wiki();
        createdWiki.setId("test-wiki-id");
        createdWiki.setRepositoryUrl(repoUrl);
        createdWiki.setStatus(WikiStatus.PENDING);
        
        when(wikiRepository.save(any(Wiki.class)))
                .thenReturn(createdWiki);
        when(generationStatusRepository.save(any(GenerationStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Mock repository cloning and code detection
        Path repoPath = Paths.get("/tmp/test-repo");
        when(repositoryService.cloneRepository(repoUrl))
                .thenReturn(repoPath);
        
        List<CodeFile> codeFiles = new ArrayList<>();
        codeFiles.add(new CodeFile("src/Main.java", "java"));
        when(repositoryService.detectCodeFiles(repoPath))
                .thenReturn(codeFiles);
        
        // Mock wiki generation
        Wiki generatedWiki = createMockGeneratedWiki(repoUrl);
        when(wikiGeneratorService.generateWiki(eq(repoUrl), eq(repoPath)))
                .thenReturn(generatedWiki);
        
        // Mock wiki retrieval
        Wiki completedWiki = createMockGeneratedWiki(repoUrl);
        completedWiki.setId("test-wiki-id");
        completedWiki.setStatus(WikiStatus.COMPLETED);
        when(wikiRepository.findById("test-wiki-id"))
                .thenReturn(Optional.of(completedWiki));
        
        // Act - Submit wiki
        ResponseEntity<?> submitResponse = controller.submitWiki(request);
        
        // Assert - Submission successful
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitResponse.getBody()).isInstanceOf(WikiSubmissionResponse.class);
        
        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) submitResponse.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo("test-wiki-id");
        assertThat(submissionResponse.getStatus()).isEqualTo("PENDING");
        
        // Act - Retrieve wiki
        ResponseEntity<?> retrieveResponse = controller.getWiki("test-wiki-id");
        
        // Assert - Retrieval successful
        assertThat(retrieveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Verify interactions
        verify(repositoryService).validateRepositoryUrl(repoUrl);
        verify(rateLimiterService).tryAcquireGenerationSlot();
        verify(wikiRepository, atLeastOnce()).save(any(Wiki.class));
    }
    
    /**
     * Test cached retrieval: Submit → Retrieve again (no regeneration)
     * Validates that existing wikis are returned from cache without regeneration.
     */
    @Test
    void testCachedWikiRetrieval() {
        // Arrange
        String repoUrl = "https://github.com/test/cached-repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);
        
        // Mock existing wiki
        Wiki existingWiki = createMockGeneratedWiki(repoUrl);
        existingWiki.setId("cached-wiki-id");
        existingWiki.setStatus(WikiStatus.COMPLETED);
        
        when(repositoryService.validateRepositoryUrl(repoUrl))
                .thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl))
                .thenReturn(Optional.of(existingWiki));
        
        // Act - Submit wiki (should return cached)
        ResponseEntity<?> submitResponse = controller.submitWiki(request);
        
        // Assert - Returns cached wiki
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitResponse.getBody()).isInstanceOf(WikiSubmissionResponse.class);
        
        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) submitResponse.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo("cached-wiki-id");
        assertThat(submissionResponse.getStatus()).isEqualTo("COMPLETED");
        
        // Verify no generation was triggered
        verify(rateLimiterService, never()).tryAcquireGenerationSlot();
        verify(wikiGeneratorService, never()).generateWiki(anyString(), any(Path.class));
        verify(wikiRepository, never()).save(any(Wiki.class));
    }
    
    /**
     * Test invalid URL rejection
     * Validates that invalid repository URLs are rejected with appropriate error.
     */
    @Test
    void testInvalidUrlRejection() {
        // Arrange
        String invalidUrl = "https://gitlab.com/test/repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(invalidUrl);
        
        when(repositoryService.validateRepositoryUrl(invalidUrl))
                .thenReturn(ValidationResult.failure("Invalid GitHub URL format"));
        
        // Act
        ResponseEntity<?> response = controller.submitWiki(request);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Invalid GitHub URL format");
        
        // Verify no further processing
        verify(wikiRepository, never()).findByRepositoryUrl(anyString());
        verify(rateLimiterService, never()).tryAcquireGenerationSlot();
    }
    
    /**
     * Test rate limit enforcement
     * Validates that requests are rejected when rate limit is reached.
     */
    @Test
    void testRateLimitEnforcement() throws GitAPIException {
        // Arrange
        String repoUrl = "https://github.com/test/rate-limited";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);
        
        when(repositoryService.validateRepositoryUrl(repoUrl))
                .thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl))
                .thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot())
                .thenReturn(false); // Rate limit reached
        
        // Act
        ResponseEntity<?> response = controller.submitWiki(request);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).asString().contains("rate limit");
        
        // Verify no wiki was created
        verify(wikiRepository, never()).save(any(Wiki.class));
    }
    
    /**
     * Test repository size validation
     * Validates that oversized repositories are rejected.
     */
    @Test
    void testOversizedRepositoryRejection() throws GitAPIException {
        // Arrange
        String repoUrl = "https://github.com/test/large-repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);
        
        when(repositoryService.validateRepositoryUrl(repoUrl))
                .thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl))
                .thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot())
                .thenReturn(true);
        when(repositoryService.getRepositorySize(repoUrl))
                .thenThrow(new GitAPIException("Repository size exceeds 10MB limit") {});
        
        // Act
        ResponseEntity<?> response = controller.submitWiki(request);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).asString().contains("Repository validation failed");
        
        // Verify rate limiter slot was released
        verify(rateLimiterService).releaseGenerationSlot();
    }
    
    /**
     * Test wiki not found
     * Validates that 404 is returned for non-existent wikis.
     */
    @Test
    void testWikiNotFound() {
        // Arrange
        String nonExistentWikiId = "non-existent-id";
        when(wikiRepository.findById(nonExistentWikiId))
                .thenReturn(Optional.empty());
        
        // Act
        ResponseEntity<?> response = controller.getWiki(nonExistentWikiId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("Wiki not found");
    }
    
    /**
     * Test regeneration workflow
     * Validates that regeneration updates existing wiki status.
     */
    @Test
    void testWikiRegeneration() {
        // Arrange
        String wikiId = "existing-wiki-id";
        Wiki existingWiki = new Wiki();
        existingWiki.setId(wikiId);
        existingWiki.setRepositoryUrl("https://github.com/test/repo");
        existingWiki.setStatus(WikiStatus.COMPLETED);
        existingWiki.setStale(true);
        
        when(wikiRepository.findById(wikiId))
                .thenReturn(Optional.of(existingWiki));
        when(rateLimiterService.tryAcquireGenerationSlot())
                .thenReturn(true);
        when(wikiRepository.save(any(Wiki.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(generationStatusRepository.save(any(GenerationStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Act
        ResponseEntity<?> response = controller.regenerateWiki(wikiId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WikiSubmissionResponse.class);
        
        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) response.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo(wikiId);
        assertThat(submissionResponse.getStatus()).isEqualTo("IN_PROGRESS");
        
        // Verify wiki status was updated (called at least once in regenerateWiki method)
        verify(wikiRepository, atLeastOnce()).save(any(Wiki.class));
        verify(generationStatusRepository, atLeastOnce()).save(any(GenerationStatus.class));
    }
    
    // Helper methods
    
    private Wiki createMockGeneratedWiki(String repoUrl) {
        Wiki wiki = new Wiki();
        wiki.setRepositoryUrl(repoUrl);
        wiki.setRepositoryName(extractRepoName(repoUrl));
        wiki.setLastCommitHash("abc123def456");
        wiki.setStatus(WikiStatus.COMPLETED);
        
        // Add mock sections
        WikiSection overviewSection = new WikiSection();
        overviewSection.setId("section-1");
        overviewSection.setSectionType(SectionType.OVERVIEW);
        overviewSection.setTitle("Overview");
        overviewSection.setContent("This is a test repository");
        overviewSection.setOrderIndex(0);
        wiki.getSections().add(overviewSection);
        
        WikiSection archSection = new WikiSection();
        archSection.setId("section-2");
        archSection.setSectionType(SectionType.ARCHITECTURE);
        archSection.setTitle("Architecture");
        archSection.setContent("Architecture details");
        archSection.setOrderIndex(1);
        wiki.getSections().add(archSection);
        
        // Add mock file explanation
        FileExplanation fileExp = new FileExplanation();
        fileExp.setId("file-1");
        fileExp.setFilePath("src/Main.java");
        fileExp.setLanguage("java");
        fileExp.setExplanation("Main entry point");
        fileExp.setCodeSnippet("public class Main { }");
        wiki.getFileExplanations().add(fileExp);
        
        return wiki;
    }
    
    private String extractRepoName(String url) {
        String[] parts = url.split("/");
        if (parts.length >= 5) {
            return parts[3] + "/" + parts[4];
        }
        return "unknown/repo";
    }
}
