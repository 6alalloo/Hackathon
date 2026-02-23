package com.codewiki.service;

import com.codewiki.model.Wiki;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.WikiRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeTry;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryMonitorServicePropertyTest {

    private WikiRepository wikiRepository;
    private RepositoryMonitorServiceImpl monitorService;
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @BeforeTry
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

    @Property(tries = 100)
    void repositoryUpdateDetection(
            @ForAll @AlphaChars @StringLength(min = 40, max = 40) String storedHash,
            @ForAll @AlphaChars @StringLength(min = 40, max = 40) String remoteHash) {
        String repoUrl = "https://github.com/testowner/testrepo";

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", remoteHash);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));

        boolean isUpdated = monitorService.isRepositoryUpdated(repoUrl, storedHash);
        assertThat(isUpdated).isEqualTo(!storedHash.equals(remoteHash));
    }

    @Property(tries = 100)
    void staleWikiMarking(
            @ForAll("wikiIds") String wikiId,
            @ForAll @AlphaChars @StringLength(min = 40, max = 40) String storedHash,
            @ForAll @AlphaChars @StringLength(min = 40, max = 40) String remoteHash) {
        Assume.that(!storedHash.equals(remoteHash));

        Wiki wiki = createTestWiki(wikiId, "https://github.com/owner/repo", storedHash);
        when(wikiRepository.findById(wikiId)).thenReturn(Optional.of(wiki));
        when(wikiRepository.save(any(Wiki.class))).thenAnswer(inv -> inv.getArgument(0));

        monitorService.markWikiAsStale(wikiId);

        ArgumentCaptor<Wiki> wikiCaptor = ArgumentCaptor.forClass(Wiki.class);
        verify(wikiRepository).save(wikiCaptor.capture());
        assertThat(wikiCaptor.getValue().isStale()).isTrue();
    }

    @Property(tries = 100)
    void scheduledCheckProcessesAllWikis(@ForAll("wikiLists") List<Wiki> wikis) {
        when(wikiRepository.findAll()).thenReturn(wikis);

        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("sha", "sameHash");
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(apiResponse));

        monitorService.checkForUpdates();

        long wikisWithHash = wikis.stream()
                .filter(w -> w.getLastCommitHash() != null && !w.getLastCommitHash().isEmpty())
                .count();
        verify(requestHeadersUriSpec, atLeast((int) wikisWithHash)).uri(anyString());
    }

    @Property(tries = 100)
    void invalidUrlsHandledGracefully(@ForAll("invalidUrls") String invalidUrl) {
        boolean result = monitorService.isRepositoryUpdated(invalidUrl, "abc123");
        assertThat(result).isFalse();
    }

    @Property(tries = 100)
    void nullParametersHandledSafely(@ForAll boolean urlIsNull, @ForAll boolean hashIsNull) {
        String url = urlIsNull ? null : "https://github.com/owner/repo";
        String hash = hashIsNull ? null : "abc123";
        boolean result = monitorService.isRepositoryUpdated(url, hash);
        assertThat(result).isFalse();
    }

    @Provide
    Arbitrary<String> wikiIds() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(10).ofMaxLength(36);
    }

    @Provide
    Arbitrary<List<Wiki>> wikiLists() {
        return Arbitraries.integers().between(0, 5).flatMap(size -> {
            List<Wiki> wikis = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                wikis.add(createTestWiki("wiki-" + i, "https://github.com/owner/repo" + i, "hash" + i));
            }
            return Arbitraries.just(wikis);
        });
    }

    @Provide
    Arbitrary<String> invalidUrls() {
        return Arbitraries.of(
                "not-a-url",
                "https://gitlab.com/owner/repo",
                "https://github.com/",
                "https://github.com/owner",
                "ftp://github.com/owner/repo",
                ""
        );
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
