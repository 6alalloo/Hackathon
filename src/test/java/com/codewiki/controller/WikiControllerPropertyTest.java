package com.codewiki.controller;

import com.codewiki.dto.GenerationStatusResponse;
import com.codewiki.dto.WikiResponse;
import com.codewiki.dto.WikiSubmissionRequest;
import com.codewiki.dto.WikiSubmissionResponse;
import com.codewiki.model.*;
import com.codewiki.repository.GenerationStatusRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.service.RateLimiterService;
import com.codewiki.service.RepositoryService;
import com.codewiki.service.WikiGeneratorService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for WikiController.
 * Tests universal properties that should hold for all valid inputs.
 */
class WikiControllerPropertyTest {
    
    private WikiController controller;
    private WikiRepository wikiRepository;
    private GenerationStatusRepository generationStatusRepository;
    private RepositoryService repositoryService;
    private WikiGeneratorService wikiGeneratorService;
    private RateLimiterService rateLimiterService;
    
    @BeforeProperty
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
     * Feature: codewiki-generator, Property 2: Valid URL Initiates Generation
     * For any valid GitHub repository URL, submitting it to the system should 
     * initiate wiki generation (status transitions to IN_PROGRESS or PENDING).
     * 
     * **Validates: Requirements 1.4**
     */
    @Property(tries = 100)
    void validUrlInitiatesGeneration(@ForAll("validGitHubUrls") String url) throws GitAPIException {
        // Arrange
        when(repositoryService.validateRepositoryUrl(url))
                .thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(url))
                .thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot())
                .thenReturn(true);
        when(repositoryService.getRepositorySize(url))
                .thenReturn(5_000_000L); // 5MB - under limit
        
        ArgumentCaptor<Wiki> wikiCaptor = ArgumentCaptor.forClass(Wiki.class);
        when(wikiRepository.save(wikiCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        WikiSubmissionRequest request = new WikiSubmissionRequest(url);
        
        // Act
        ResponseEntity<?> response = controller.submitWiki(request);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOf(WikiSubmissionResponse.class);
        
        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) response.getBody();
        assertThat(submissionResponse.getStatus()).isIn("PENDING", "IN_PROGRESS");
        assertThat(submissionResponse.getWikiId()).isNotNull();
        
        // Verify wiki was created with correct status
        Wiki savedWiki = wikiCaptor.getValue();
        assertThat(savedWiki.getStatus()).isIn(WikiStatus.PENDING, WikiStatus.IN_PROGRESS);
        assertThat(savedWiki.getRepositoryUrl()).isEqualTo(url);
    }
    
    /**
     * Feature: codewiki-generator, Property 16: Wiki Cache Retrieval
     * For any repository that has been previously processed, requesting its wiki 
     * should retrieve the cached Wiki_Content from the database without triggering 
     * LLM API calls or regeneration.
     * 
     * **Validates: Requirements 7.3, 7.4**
     */
    @Property(tries = 100)
    void cachedWikiRetrievalWithoutRegeneration(@ForAll("validGitHubUrls") String url) {
        // Arrange - Create existing wiki
        Wiki existingWiki = createMockWiki(url, WikiStatus.COMPLETED);
        
        when(repositoryService.validateRepositoryUrl(url))
                .thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(url))
                .thenReturn(Optional.of(existingWiki));
        
        WikiSubmissionRequest request = new WikiSubmissionRequest(url);
        
        // Act
        ResponseEntity<?> response = controller.submitWiki(request);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WikiSubmissionResponse.class);
        
        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) response.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo(existingWiki.getId());
        assertThat(submissionResponse.getStatus()).isEqualTo(WikiStatus.COMPLETED.name());
        
        // Verify no new wiki was created and no generation slot was acquired
        verify(wikiRepository, never()).save(any(Wiki.class));
        verify(rateLimiterService, never()).tryAcquireGenerationSlot();
        verify(wikiGeneratorService, never()).generateWiki(anyString(), any(Path.class));
    }
    
    /**
     * Feature: codewiki-generator, Property 38: Generation Status Tracking
     * For any wiki generation in progress, the system should maintain and expose 
     * status information including current phase and overall status.
     * 
     * **Validates: Requirements 18.1, 18.2**
     */
    @Property(tries = 100)
    void generationStatusTracking(@ForAll("wikiIds") String wikiId,
                                   @ForAll("generationPhases") String phase) {
        // Arrange
        Wiki wiki = new Wiki();
        wiki.setId(wikiId);
        wiki.setStatus(WikiStatus.IN_PROGRESS);
        wiki.setRepositoryUrl("https://github.com/test/repo");
        
        GenerationStatus status = new GenerationStatus();
        status.setWikiId(wikiId);
        status.setPhase(phase);
        status.setStatus("IN_PROGRESS");
        
        when(wikiRepository.findById(wikiId))
                .thenReturn(Optional.of(wiki));
        when(generationStatusRepository.findAll())
                .thenReturn(List.of(status));
        
        // Act
        ResponseEntity<?> response = controller.getGenerationStatus(wikiId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(GenerationStatusResponse.class);
        
        GenerationStatusResponse statusResponse = (GenerationStatusResponse) response.getBody();
        assertThat(statusResponse.getStatus()).isNotNull();
        assertThat(statusResponse.getPhase()).isEqualTo(phase);
    }
    
    /**
     * Feature: codewiki-generator, Property 39: Generation Completion Status
     * For any wiki generation that completes successfully, the status should 
     * transition to COMPLETED and the wiki should be displayable.
     * 
     * **Validates: Requirements 18.3**
     */
    @Property(tries = 100)
    void completedGenerationStatus(@ForAll("wikiIds") String wikiId) {
        // Arrange
        Wiki completedWiki = new Wiki();
        completedWiki.setId(wikiId);
        completedWiki.setStatus(WikiStatus.COMPLETED);
        completedWiki.setRepositoryUrl("https://github.com/test/repo");
        completedWiki.setRepositoryName("test/repo");
        
        // Add sections and file explanations to make it displayable
        WikiSection section = new WikiSection();
        section.setId("section-1");
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Overview");
        section.setContent("Test content");
        completedWiki.getSections().add(section);
        
        when(wikiRepository.findById(wikiId))
                .thenReturn(Optional.of(completedWiki));
        
        // Act - Get wiki
        ResponseEntity<?> response = controller.getWiki(wikiId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WikiResponse.class);
        
        WikiResponse wikiResponse = (WikiResponse) response.getBody();
        assertThat(wikiResponse.getStatus()).isEqualTo("COMPLETED");
        assertThat(wikiResponse.getId()).isEqualTo(wikiId);
        assertThat(wikiResponse.getSections()).isNotEmpty();
    }
    
    /**
     * Feature: codewiki-generator, Property 40: Generation Failure Status
     * For any wiki generation that fails, the status should transition to FAILED 
     * and error details should be included in the status response.
     * 
     * **Validates: Requirements 18.4**
     */
    @Property(tries = 100)
    void failedGenerationStatus(@ForAll("wikiIds") String wikiId,
                                 @ForAll("errorMessages") String errorMessage) {
        // Arrange
        Wiki failedWiki = new Wiki();
        failedWiki.setId(wikiId);
        failedWiki.setStatus(WikiStatus.FAILED);
        failedWiki.setRepositoryUrl("https://github.com/test/repo");
        
        GenerationStatus status = new GenerationStatus();
        status.setWikiId(wikiId);
        status.setPhase("Generation failed");
        status.setStatus("FAILED");
        status.setErrorMessage(errorMessage);
        
        when(wikiRepository.findById(wikiId))
                .thenReturn(Optional.of(failedWiki));
        when(generationStatusRepository.findAll())
                .thenReturn(List.of(status));
        
        // Act
        ResponseEntity<?> response = controller.getGenerationStatus(wikiId);
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(GenerationStatusResponse.class);
        
        GenerationStatusResponse statusResponse = (GenerationStatusResponse) response.getBody();
        assertThat(statusResponse.getStatus()).isEqualTo("FAILED");
        assertThat(statusResponse.getErrorMessage()).isEqualTo(errorMessage);
    }
    
    /**
     * Feature: codewiki-generator, Property 41: Status Persistence
     * For any wiki generation, the status should be persisted and retrievable 
     * even after the user navigates away and returns later.
     * 
     * **Validates: Requirements 18.5**
     */
    @Property(tries = 100)
    void statusPersistence(@ForAll("wikiIds") String wikiId,
                           @ForAll("wikiStatuses") WikiStatus status) {
        // Arrange - Simulate wiki created at some point in the past
        Wiki wiki = new Wiki();
        wiki.setId(wikiId);
        wiki.setStatus(status);
        wiki.setRepositoryUrl("https://github.com/test/repo");
        wiki.setCreatedAt(LocalDateTime.now().minusHours(1));
        
        when(wikiRepository.findById(wikiId))
                .thenReturn(Optional.of(wiki));
        when(generationStatusRepository.findAll())
                .thenReturn(new ArrayList<>());
        
        // Act - Retrieve status after time has passed
        ResponseEntity<?> response = controller.getGenerationStatus(wikiId);
        
        // Assert - Status should still be retrievable
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(GenerationStatusResponse.class);
        
        GenerationStatusResponse statusResponse = (GenerationStatusResponse) response.getBody();
        assertThat(statusResponse.getStatus()).isEqualTo(status.name());
    }
    
    // ========== Arbitraries (Generators) ==========
    
    @Provide
    Arbitrary<String> validGitHubUrls() {
        Arbitrary<String> owners = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> repos = Arbitraries.strings()
                .alpha().numeric()
                .ofMinLength(1).ofMaxLength(30);
        
        return Combinators.combine(owners, repos)
                .as((owner, repo) -> "https://github.com/" + owner + "/" + repo);
    }
    
    @Provide
    Arbitrary<String> wikiIds() {
        return Arbitraries.strings()
                .alpha().numeric().withChars('-')
                .ofMinLength(10).ofMaxLength(36);
    }
    
    @Provide
    Arbitrary<String> generationPhases() {
        return Arbitraries.of(
                "Initializing",
                "Cloning repository",
                "Detecting code files",
                "Generating overview",
                "Generating architecture",
                "Generating file explanations",
                "Generating component interactions",
                "Assembling wiki",
                "Generation complete"
        );
    }
    
    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "Repository not found",
                "Network timeout",
                "LLM API error",
                "No code files detected",
                "Repository too large",
                "Invalid repository structure"
        );
    }
    
    @Provide
    Arbitrary<WikiStatus> wikiStatuses() {
        return Arbitraries.of(WikiStatus.values());
    }
    
    // ========== Helper Methods ==========
    
    private Wiki createMockWiki(String url, WikiStatus status) {
        Wiki wiki = new Wiki();
        wiki.setId("wiki-" + url.hashCode());
        wiki.setRepositoryUrl(url);
        wiki.setRepositoryName(extractRepoName(url));
        wiki.setStatus(status);
        wiki.setLastCommitHash("abc123");
        
        // Add mock sections
        WikiSection section = new WikiSection();
        section.setId("section-1");
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("Overview");
        section.setContent("Mock overview content");
        wiki.getSections().add(section);
        
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
