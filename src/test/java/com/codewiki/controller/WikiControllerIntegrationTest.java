package com.codewiki.controller;

import com.codewiki.dto.WikiSubmissionRequest;
import com.codewiki.dto.WikiSubmissionResponse;
import com.codewiki.model.GenerationPhase;
import com.codewiki.model.GenerationState;
import com.codewiki.model.GenerationStatus;
import com.codewiki.model.ValidationResult;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.GenerationStatusRepository;
import com.codewiki.repository.WikiRepository;
import com.codewiki.service.RateLimiterService;
import com.codewiki.service.RepositoryService;
import com.codewiki.service.WikiGenerationOrchestratorService;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests for WikiController with mocked dependencies.
 */
class WikiControllerIntegrationTest {

    private WikiController controller;
    private WikiRepository wikiRepository;
    private GenerationStatusRepository generationStatusRepository;
    private RepositoryService repositoryService;
    private WikiGenerationOrchestratorService wikiGenerationOrchestratorService;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        wikiRepository = mock(WikiRepository.class);
        generationStatusRepository = mock(GenerationStatusRepository.class);
        repositoryService = mock(RepositoryService.class);
        wikiGenerationOrchestratorService = mock(WikiGenerationOrchestratorService.class);
        rateLimiterService = mock(RateLimiterService.class);

        controller = new WikiController(
                wikiRepository,
                generationStatusRepository,
                repositoryService,
                wikiGenerationOrchestratorService,
                rateLimiterService
        );
    }

    @Test
    void testEndToEndWikiGeneration() throws GitAPIException {
        String repoUrl = "https://github.com/test/repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);

        when(repositoryService.validateRepositoryUrl(repoUrl)).thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl)).thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot()).thenReturn(true);
        when(repositoryService.getRepositorySize(repoUrl)).thenReturn(5_000_000L);

        Wiki createdWiki = new Wiki();
        createdWiki.setId("test-wiki-id");
        createdWiki.setRepositoryUrl(repoUrl);
        createdWiki.setStatus(WikiStatus.PENDING);

        when(wikiRepository.save(any(Wiki.class))).thenReturn(createdWiki);
        when(generationStatusRepository.save(any(GenerationStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Wiki completedWiki = new Wiki();
        completedWiki.setId("test-wiki-id");
        completedWiki.setRepositoryUrl(repoUrl);
        completedWiki.setRepositoryName("test/repo");
        completedWiki.setStatus(WikiStatus.COMPLETED);
        when(wikiRepository.findById("test-wiki-id")).thenReturn(Optional.of(completedWiki));

        ResponseEntity<?> submitResponse = controller.submitWiki(request);
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitResponse.getBody()).isInstanceOf(WikiSubmissionResponse.class);

        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) submitResponse.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo("test-wiki-id");
        assertThat(submissionResponse.getStatus()).isEqualTo("PENDING");

        ResponseEntity<?> retrieveResponse = controller.getWiki("test-wiki-id");
        assertThat(retrieveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(repositoryService).validateRepositoryUrl(repoUrl);
        verify(rateLimiterService).tryAcquireGenerationSlot();
        verify(wikiRepository, atLeastOnce()).save(any(Wiki.class));
        verify(wikiGenerationOrchestratorService).generateWikiAsync("test-wiki-id", repoUrl);
    }

    @Test
    void testCachedWikiRetrieval() {
        String repoUrl = "https://github.com/test/cached-repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);

        Wiki existingWiki = new Wiki();
        existingWiki.setId("cached-wiki-id");
        existingWiki.setRepositoryUrl(repoUrl);
        existingWiki.setStatus(WikiStatus.COMPLETED);

        when(repositoryService.validateRepositoryUrl(repoUrl)).thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl)).thenReturn(Optional.of(existingWiki));

        ResponseEntity<?> submitResponse = controller.submitWiki(request);
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) submitResponse.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo("cached-wiki-id");
        assertThat(submissionResponse.getStatus()).isEqualTo("COMPLETED");

        verify(rateLimiterService, never()).tryAcquireGenerationSlot();
        verify(wikiGenerationOrchestratorService, never()).generateWikiAsync(anyString(), anyString());
        verify(wikiRepository, never()).save(any(Wiki.class));
    }

    @Test
    void testInvalidUrlRejection() {
        String invalidUrl = "https://gitlab.com/test/repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(invalidUrl);

        when(repositoryService.validateRepositoryUrl(invalidUrl))
                .thenReturn(ValidationResult.failure("Invalid GitHub URL format"));

        ResponseEntity<?> response = controller.submitWiki(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("Invalid GitHub URL format");

        verify(wikiRepository, never()).findByRepositoryUrl(anyString());
        verify(rateLimiterService, never()).tryAcquireGenerationSlot();
    }

    @Test
    void testRateLimitEnforcement() {
        String repoUrl = "https://github.com/test/rate-limited";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);

        when(repositoryService.validateRepositoryUrl(repoUrl)).thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl)).thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot()).thenReturn(false);

        ResponseEntity<?> response = controller.submitWiki(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).asString().contains("rate limit");
        verify(wikiRepository, never()).save(any(Wiki.class));
    }

    @Test
    void testOversizedRepositoryRejection() throws GitAPIException {
        String repoUrl = "https://github.com/test/large-repo";
        WikiSubmissionRequest request = new WikiSubmissionRequest(repoUrl);

        when(repositoryService.validateRepositoryUrl(repoUrl)).thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(repoUrl)).thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot()).thenReturn(true);
        when(repositoryService.getRepositorySize(repoUrl))
                .thenThrow(new GitAPIException("Repository size exceeds 10MB limit") {});

        ResponseEntity<?> response = controller.submitWiki(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).asString().contains("Repository validation failed");
        verify(rateLimiterService).releaseGenerationSlot();
    }

    @Test
    void testWikiNotFound() {
        String nonExistentWikiId = "non-existent-id";
        when(wikiRepository.findById(nonExistentWikiId)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getWiki(nonExistentWikiId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("Wiki not found");
    }

    @Test
    void testWikiRegeneration() {
        String wikiId = "existing-wiki-id";
        Wiki existingWiki = new Wiki();
        existingWiki.setId(wikiId);
        existingWiki.setRepositoryUrl("https://github.com/test/repo");
        existingWiki.setStatus(WikiStatus.COMPLETED);
        existingWiki.setStale(true);

        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(existingWiki));
        when(rateLimiterService.tryAcquireGenerationSlot()).thenReturn(true);
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(generationStatusRepository.save(any(GenerationStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.regenerateWiki(wikiId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WikiSubmissionResponse.class);

        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) response.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo(wikiId);
        assertThat(submissionResponse.getStatus()).isEqualTo("IN_PROGRESS");

        verify(wikiRepository, atLeastOnce()).save(any(Wiki.class));
        verify(generationStatusRepository, atLeastOnce()).save(any(GenerationStatus.class));
        verify(wikiGenerationOrchestratorService).generateWikiAsync(wikiId, existingWiki.getRepositoryUrl());
    }

    @Test
    void testGetGenerationStatusUsesLatestRecord() {
        String wikiId = "wiki-1";
        Wiki wiki = new Wiki();
        wiki.setId(wikiId);
        wiki.setStatus(WikiStatus.IN_PROGRESS);
        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(wiki));

        GenerationStatus latest = new GenerationStatus();
        latest.setWikiId(wikiId);
        latest.setPhase(GenerationPhase.GENERATING_WIKI_CONTENT);
        latest.setStatus(GenerationState.IN_PROGRESS);
        when(generationStatusRepository.findTopByWikiIdOrderByUpdatedAtDesc(wikiId))
                .thenReturn(Optional.of(latest));

        ResponseEntity<?> response = controller.getGenerationStatus(wikiId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
