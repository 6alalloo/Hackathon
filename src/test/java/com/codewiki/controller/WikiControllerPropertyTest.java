package com.codewiki.controller;

import com.codewiki.dto.GenerationStatusResponse;
import com.codewiki.dto.WikiResponse;
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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiControllerPropertyTest {

    private WikiController controller;
    private WikiRepository wikiRepository;
    private GenerationStatusRepository generationStatusRepository;
    private RepositoryService repositoryService;
    private WikiGenerationOrchestratorService wikiGenerationOrchestratorService;
    private RateLimiterService rateLimiterService;

    @BeforeTry
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

    @Property(tries = 100)
    void validUrlInitiatesGeneration(@ForAll("validGitHubUrls") String url) throws GitAPIException {
        when(repositoryService.validateRepositoryUrl(url)).thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(url)).thenReturn(Optional.empty());
        when(rateLimiterService.tryAcquireGenerationSlot()).thenReturn(true);
        when(repositoryService.getRepositorySize(url)).thenReturn(5_000_000L);

        ArgumentCaptor<Wiki> wikiCaptor = ArgumentCaptor.forClass(Wiki.class);
        when(wikiRepository.save(wikiCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.submitWiki(new WikiSubmissionRequest(url));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOf(WikiSubmissionResponse.class);
        verify(wikiGenerationOrchestratorService).generateWikiAsync(anyString(), anyString());

        Wiki savedWiki = wikiCaptor.getValue();
        assertThat(savedWiki.getRepositoryUrl()).isEqualTo(url);
        assertThat(savedWiki.getStatus()).isEqualTo(WikiStatus.PENDING);
    }

    @Property(tries = 100)
    void cachedWikiRetrievalWithoutRegeneration(@ForAll("validGitHubUrls") String url) {
        Wiki existingWiki = createMockWiki(url, WikiStatus.COMPLETED);

        when(repositoryService.validateRepositoryUrl(url)).thenReturn(ValidationResult.success());
        when(wikiRepository.findByRepositoryUrl(url)).thenReturn(Optional.of(existingWiki));

        ResponseEntity<?> response = controller.submitWiki(new WikiSubmissionRequest(url));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WikiSubmissionResponse.class);

        WikiSubmissionResponse submissionResponse = (WikiSubmissionResponse) response.getBody();
        assertThat(submissionResponse.getWikiId()).isEqualTo(existingWiki.getId());
        assertThat(submissionResponse.getStatus()).isEqualTo(WikiStatus.COMPLETED.name());

        verify(wikiRepository, never()).save(any(Wiki.class));
        verify(rateLimiterService, never()).tryAcquireGenerationSlot();
        verify(wikiGenerationOrchestratorService, never()).generateWikiAsync(anyString(), anyString());
    }

    @Property(tries = 100)
    void generationStatusTracking(@ForAll("wikiIds") String wikiId,
                                  @ForAll("generationPhases") GenerationPhase phase) {
        Wiki wiki = new Wiki();
        wiki.setId(wikiId);
        wiki.setStatus(WikiStatus.IN_PROGRESS);
        wiki.setRepositoryUrl("https://github.com/test/repo");

        GenerationStatus status = new GenerationStatus();
        status.setWikiId(wikiId);
        status.setPhase(phase);
        status.setStatus(GenerationState.IN_PROGRESS);

        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(wiki));
        when(generationStatusRepository.findTopByWikiIdOrderByUpdatedAtDesc(wikiId))
                .thenReturn(Optional.of(status));

        ResponseEntity<?> response = controller.getGenerationStatus(wikiId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(GenerationStatusResponse.class);

        GenerationStatusResponse statusResponse = (GenerationStatusResponse) response.getBody();
        assertThat(statusResponse.getStatus()).isEqualTo(GenerationState.IN_PROGRESS);
        assertThat(statusResponse.getPhase()).isEqualTo(phase);
    }

    @Property(tries = 100)
    void completedGenerationStatus(@ForAll("wikiIds") String wikiId) {
        Wiki completedWiki = new Wiki();
        completedWiki.setId(wikiId);
        completedWiki.setStatus(WikiStatus.COMPLETED);
        completedWiki.setRepositoryUrl("https://github.com/test/repo");
        completedWiki.setRepositoryName("test/repo");

        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(completedWiki));

        ResponseEntity<?> response = controller.getWiki(wikiId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(WikiResponse.class);

        WikiResponse wikiResponse = (WikiResponse) response.getBody();
        assertThat(wikiResponse.getStatus()).isEqualTo("COMPLETED");
        assertThat(wikiResponse.getId()).isEqualTo(wikiId);
    }

    @Property(tries = 100)
    void statusPersistence(@ForAll("wikiIds") String wikiId,
                           @ForAll("wikiStatuses") WikiStatus status) {
        Wiki wiki = new Wiki();
        wiki.setId(wikiId);
        wiki.setStatus(status);
        wiki.setRepositoryUrl("https://github.com/test/repo");
        wiki.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(wiki));
        when(generationStatusRepository.findTopByWikiIdOrderByUpdatedAtDesc(wikiId))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getGenerationStatus(wikiId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(GenerationStatusResponse.class);
    }

    @Provide
    Arbitrary<String> validGitHubUrls() {
        Arbitrary<String> owners = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> repos = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
        return Combinators.combine(owners, repos).as((owner, repo) -> "https://github.com/" + owner + "/" + repo);
    }

    @Provide
    Arbitrary<String> wikiIds() {
        return Arbitraries.strings().alpha().numeric().withChars('-').ofMinLength(10).ofMaxLength(36);
    }

    @Provide
    Arbitrary<GenerationPhase> generationPhases() {
        return Arbitraries.of(GenerationPhase.values());
    }

    @Provide
    Arbitrary<WikiStatus> wikiStatuses() {
        return Arbitraries.of(WikiStatus.values());
    }

    private Wiki createMockWiki(String url, WikiStatus status) {
        Wiki wiki = new Wiki();
        wiki.setId("wiki-" + url.hashCode());
        wiki.setRepositoryUrl(url);
        wiki.setRepositoryName(extractRepoName(url));
        wiki.setStatus(status);
        wiki.setLastCommitHash("abc123");
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
