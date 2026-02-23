package com.codewiki.service;

import com.codewiki.model.Wiki;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.WikiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RepositoryMonitorService.
 * Tests specific scenarios and edge cases for repository monitoring.
 */
class RepositoryMonitorServiceTest {
    
    private WikiRepository wikiRepository;
    private RepositoryMonitorServiceImpl monitorService;
    private WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;
    
    @BeforeEach
    void setUp() {
        wikiRepository = mock(WikiRepository.class);
        
        // Mock WebClient chain
        webClientBuilder = mock(WebClient.Builder.class);
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
        
        when(webClientBuilder.baseUrl(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        monitorService = new RepositoryMonitorServiceImpl();
        ReflectionTestUtils.setField(monitorService, "wikiRepository", wikiRepository);
    }
    
    @Test
    void isRepositoryUpdated_whenHashesDiffer_returnsTrue() {
        // Given
        String repoUrl = "https://github.com/owner/repo";
        String storedHash = "abc123";
        String remoteHash = "def456";
        
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", remoteHash);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));
        
        // When
        boolean result = monitorService.isRepositoryUpdated(repoUrl, storedHash);
        
        // Then
        assertThat(result).isTrue();
        verify(requestHeadersUriSpec).uri("/repos/owner/repo/commits/HEAD");
    }
    
    @Test
    void isRepositoryUpdated_whenHashesMatch_returnsFalse() {
        // Given
        String repoUrl = "https://github.com/owner/repo";
        String storedHash = "abc123";
        String remoteHash = "abc123";
        
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", remoteHash);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));
        
        // When
        boolean result = monitorService.isRepositoryUpdated(repoUrl, storedHash);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void isRepositoryUpdated_withInvalidUrl_returnsFalse() {
        // Given
        String invalidUrl = "not-a-valid-url";
        String storedHash = "abc123";
        
        // When
        boolean result = monitorService.isRepositoryUpdated(invalidUrl, storedHash);
        
        // Then
        assertThat(result).isFalse();
        verify(requestHeadersUriSpec, never()).uri(anyString());
    }
    
    @Test
    void isRepositoryUpdated_withNullUrl_returnsFalse() {
        // Given
        String storedHash = "abc123";
        
        // When
        boolean result = monitorService.isRepositoryUpdated(null, storedHash);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void isRepositoryUpdated_withNullHash_returnsFalse() {
        // Given
        String repoUrl = "https://github.com/owner/repo";
        
        // When
        boolean result = monitorService.isRepositoryUpdated(repoUrl, null);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void isRepositoryUpdated_whenGitHubApiReturnsNull_returnsFalse() {
        // Given
        String repoUrl = "https://github.com/owner/repo";
        String storedHash = "abc123";
        
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.empty());
        
        // When
        boolean result = monitorService.isRepositoryUpdated(repoUrl, storedHash);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void isRepositoryUpdated_whenGitHubApiThrowsException_returnsFalse() {
        // Given
        String repoUrl = "https://github.com/owner/repo";
        String storedHash = "abc123";
        
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("API error")));
        
        // When
        boolean result = monitorService.isRepositoryUpdated(repoUrl, storedHash);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void isRepositoryUpdated_withGitExtension_handlesCorrectly() {
        // Given
        String repoUrl = "https://github.com/owner/repo.git";
        String storedHash = "abc123";
        String remoteHash = "def456";
        
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", remoteHash);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));
        
        // When
        boolean result = monitorService.isRepositoryUpdated(repoUrl, storedHash);
        
        // Then
        assertThat(result).isTrue();
        verify(requestHeadersUriSpec).uri("/repos/owner/repo/commits/HEAD");
    }
    
    @Test
    void markWikiAsStale_setsStaleFlag() {
        // Given
        String wikiId = "wiki-123";
        Wiki wiki = createTestWiki(wikiId, "https://github.com/owner/repo", "abc123");
        wiki.setStale(false);
        
        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(wiki));
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When
        monitorService.markWikiAsStale(wikiId);
        
        // Then
        ArgumentCaptor<Wiki> wikiCaptor = ArgumentCaptor.forClass(Wiki.class);
        verify(wikiRepository).save(wikiCaptor.capture());
        
        Wiki savedWiki = wikiCaptor.getValue();
        assertThat(savedWiki.isStale()).isTrue();
        assertThat(savedWiki.getId()).isEqualTo(wikiId);
    }
    
    @Test
    void markWikiAsStale_whenWikiNotFound_throwsException() {
        // Given
        String wikiId = "nonexistent-wiki";
        when(wikiRepository.findById(wikiId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> monitorService.markWikiAsStale(wikiId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wiki not found");
    }
    
    @Test
    void checkForUpdates_processesAllWikisWithCommitHash() {
        // Given
        Wiki wiki1 = createTestWiki("wiki-1", "https://github.com/owner/repo1", "hash1");
        Wiki wiki2 = createTestWiki("wiki-2", "https://github.com/owner/repo2", "hash2");
        Wiki wiki3 = createTestWiki("wiki-3", "https://github.com/owner/repo3", null); // No hash
        
        when(wikiRepository.findAll()).thenReturn(Arrays.asList(wiki1, wiki2, wiki3));
        
        // Mock GitHub API to return same hashes (no updates)
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "hash1");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));
        
        // When
        monitorService.checkForUpdates();
        
        // Then
        // Should check wiki1 and wiki2, but not wiki3 (no commit hash)
        verify(requestHeadersUriSpec, times(2)).uri(anyString());
    }
    
    @Test
    void checkForUpdates_marksUpdatedWikisAsStale() {
        // Given
        Wiki wiki1 = createTestWiki("wiki-1", "https://github.com/owner/repo1", "oldHash");
        
        when(wikiRepository.findAll()).thenReturn(Collections.singletonList(wiki1));
        when(wikiRepository.findById("wiki-1")).thenReturn(Optional.of(wiki1));
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Mock GitHub API to return different hash (update detected)
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "newHash");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));
        
        // When
        monitorService.checkForUpdates();
        
        // Then
        verify(wikiRepository).save(argThat(wiki -> wiki.isStale()));
    }
    
    @Test
    void checkForUpdates_continuesOnIndividualFailures() {
        // Given
        Wiki wiki1 = createTestWiki("wiki-1", "https://github.com/owner/repo1", "hash1");
        Wiki wiki2 = createTestWiki("wiki-2", "https://github.com/owner/repo2", "hash2");
        
        when(wikiRepository.findAll()).thenReturn(Arrays.asList(wiki1, wiki2));
        
        // First call throws exception, second succeeds
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "hash2");
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("API error")))
                .thenReturn(Mono.just(apiResponse));
        
        // When
        monitorService.checkForUpdates();
        
        // Then
        // Should attempt to check both wikis despite first failure
        verify(requestHeadersUriSpec, times(2)).uri(anyString());
    }
    
    @Test
    void checkForUpdates_handlesEmptyWikiList() {
        // Given
        when(wikiRepository.findAll()).thenReturn(Collections.emptyList());
        
        // When
        monitorService.checkForUpdates();
        
        // Then
        verify(requestHeadersUriSpec, never()).uri(anyString());
    }
    
    // Helper methods
    
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
