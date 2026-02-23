package com.codewiki.service;

import com.codewiki.model.Wiki;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.WikiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryMonitorServiceTest {

    private WikiRepository wikiRepository;
    private RepositoryMonitorServiceImpl monitorService;
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        wikiRepository = mock(WikiRepository.class);
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        monitorService = new RepositoryMonitorServiceImpl(wikiRepository, webClient);
    }

    @Test
    void isRepositoryUpdated_whenHashesDiffer_returnsTrue() {
        String repoUrl = "https://github.com/owner/repo";
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "def456");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));

        boolean result = monitorService.isRepositoryUpdated(repoUrl, "abc123");

        assertThat(result).isTrue();
        verify(requestHeadersUriSpec).uri("/repos/owner/repo/commits/HEAD");
    }

    @Test
    void isRepositoryUpdated_whenHashesMatch_returnsFalse() {
        String repoUrl = "https://github.com/owner/repo";
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "abc123");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));

        boolean result = monitorService.isRepositoryUpdated(repoUrl, "abc123");
        assertThat(result).isFalse();
    }

    @Test
    void isRepositoryUpdated_withInvalidUrl_returnsFalse() {
        boolean result = monitorService.isRepositoryUpdated("not-a-valid-url", "abc123");
        assertThat(result).isFalse();
        verify(requestHeadersUriSpec, never()).uri(anyString());
    }

    @Test
    void markWikiAsStale_setsStaleFlag() {
        String wikiId = "wiki-123";
        Wiki wiki = createTestWiki(wikiId, "https://github.com/owner/repo", "abc123");
        wiki.setStale(false);

        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(wiki));
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(inv -> inv.getArgument(0));

        monitorService.markWikiAsStale(wikiId);

        ArgumentCaptor<Wiki> wikiCaptor = ArgumentCaptor.forClass(Wiki.class);
        verify(wikiRepository).save(wikiCaptor.capture());
        assertThat(wikiCaptor.getValue().isStale()).isTrue();
    }

    @Test
    void markWikiAsStale_whenWikiNotFound_throwsException() {
        when(wikiRepository.findById("nonexistent-wiki")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> monitorService.markWikiAsStale("nonexistent-wiki"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wiki not found");
    }

    @Test
    void checkForUpdates_processesAllWikisWithCommitHash() {
        Wiki wiki1 = createTestWiki("wiki-1", "https://github.com/owner/repo1", "hash1");
        Wiki wiki2 = createTestWiki("wiki-2", "https://github.com/owner/repo2", "hash2");
        Wiki wiki3 = createTestWiki("wiki-3", "https://github.com/owner/repo3", null);

        when(wikiRepository.findAll()).thenReturn(Arrays.asList(wiki1, wiki2, wiki3));

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "hash1");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));

        monitorService.checkForUpdates();
        verify(requestHeadersUriSpec, times(2)).uri(anyString());
    }

    @Test
    void checkForUpdates_marksUpdatedWikisAsStale() {
        Wiki wiki1 = createTestWiki("wiki-1", "https://github.com/owner/repo1", "oldHash");
        when(wikiRepository.findAll()).thenReturn(Collections.singletonList(wiki1));
        when(wikiRepository.findById("wiki-1")).thenReturn(Optional.of(wiki1));
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "newHash");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));

        monitorService.checkForUpdates();
        verify(wikiRepository).save(any(Wiki.class));
    }

    private Wiki createTestWiki(String id, String repoUrl, String commitHash) {
        Wiki wiki = new Wiki();
        wiki.setId(id);
        wiki.setRepositoryUrl(repoUrl);
        wiki.setRepositoryName("owner/repo");
        wiki.setLastCommitHash(commitHash);
        wiki.setStatus(WikiStatus.COMPLETED);
        wiki.setStale(false);
        wiki.setCreatedAt(LocalDateTime.now());
        wiki.setUpdatedAt(LocalDateTime.now());
        return wiki;
    }
}
