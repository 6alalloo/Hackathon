package com.codewiki.service;

import com.codewiki.dto.SearchResult;
import com.codewiki.model.FileExplanation;
import com.codewiki.model.SectionType;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiSection;
import com.codewiki.model.WikiStatus;
import com.codewiki.repository.WikiRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServicePropertyTest {

    private WikiRepository wikiRepository;
    private SearchService searchService;

    @BeforeTry
    void setUp() {
        wikiRepository = mock(WikiRepository.class);
        searchService = new SearchServiceImpl(wikiRepository);
    }

    @Property(tries = 100)
    void searchAcrossAllWikis_SearchesEntireDatabase(
            @ForAll("searchableWikis") List<Wiki> wikis,
            @ForAll("searchKeywords") String keyword) {
        when(wikiRepository.findAll()).thenReturn(wikis);

        List<SearchResult> results = searchService.search(keyword);
        long wikisWithKeyword = wikis.stream().filter(wiki -> wikiContainsKeyword(wiki, keyword)).count();

        if (wikisWithKeyword > 0) {
            assertThat(results).isNotEmpty();
        }
    }

    @Property(tries = 100)
    void searchResultRanking_OrdersByRelevanceDescending(
            @ForAll("searchableWikis") List<Wiki> wikis,
            @ForAll("searchKeywords") String keyword) {
        when(wikiRepository.findAll()).thenReturn(wikis);
        List<SearchResult> results = searchService.search(keyword);

        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).getRelevanceScore())
                    .isGreaterThanOrEqualTo(results.get(i + 1).getRelevanceScore());
        }
    }

    @Property(tries = 100)
    void searchResultCompleteness_IncludesAllRequiredFields(
            @ForAll("searchableWikis") List<Wiki> wikis,
            @ForAll("searchKeywords") String keyword) {
        when(wikiRepository.findAll()).thenReturn(wikis);
        List<SearchResult> results = searchService.search(keyword);

        for (SearchResult result : results) {
            assertThat(result.getWikiId()).isNotBlank();
            assertThat(result.getRepositoryName()).isNotBlank();
            assertThat(result.getSectionReference()).isNotBlank();
            assertThat(result.getSnippet()).isNotNull();
            assertThat(result.getRelevanceScore()).isGreaterThan(0);
        }
    }

    @Property(tries = 50)
    void emptyQuery_ReturnsEmptyResults(@ForAll("emptyOrNullStrings") String emptyQuery) {
        when(wikiRepository.findAll()).thenReturn(List.of());
        List<SearchResult> results = searchService.search(emptyQuery);
        assertThat(results).isEmpty();
    }

    @Property(tries = 100)
    void searchResults_AreRelevantToQuery(
            @ForAll("searchableWikis") List<Wiki> wikis,
            @ForAll("searchKeywords") String keyword) {
        when(wikiRepository.findAll()).thenReturn(wikis);
        List<SearchResult> results = searchService.search(keyword);

        for (SearchResult result : results) {
            Wiki wiki = wikis.stream().filter(w -> w.getId().equals(result.getWikiId())).findFirst().orElse(null);
            assertThat(wiki).isNotNull();
            assertThat(wikiContainsKeyword(wiki, keyword)).isTrue();
        }
    }

    private boolean wikiContainsKeyword(Wiki wiki, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        for (WikiSection section : wiki.getSections()) {
            if (section.getTitle().toLowerCase().contains(lowerKeyword) ||
                    section.getContent().toLowerCase().contains(lowerKeyword)) {
                return true;
            }
        }
        for (FileExplanation file : wiki.getFileExplanations()) {
            if (file.getFilePath().toLowerCase().contains(lowerKeyword) ||
                    file.getExplanation().toLowerCase().contains(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    @Provide
    Arbitrary<List<Wiki>> searchableWikis() {
        return Arbitraries.integers().between(1, 5).flatMap(count -> {
            List<Arbitrary<Wiki>> wikiArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                wikiArbitraries.add(wikiWithContent());
            }
            return Combinators.combine(wikiArbitraries).as(wikis -> wikis.stream().collect(Collectors.toList()));
        });
    }

    private Arbitrary<Wiki> wikiWithContent() {
        return Combinators.combine(repositoryNames(), searchKeywords(), Arbitraries.integers().between(1, 3))
                .as((repoName, keyword, sectionCount) -> {
                    Wiki wiki = new Wiki();
                    wiki.setRepositoryUrl("https://github.com/test/" + repoName);
                    wiki.setRepositoryName(repoName);
                    wiki.setStatus(WikiStatus.COMPLETED);

                    for (int i = 0; i < sectionCount; i++) {
                        WikiSection section = new WikiSection();
                        section.setWiki(wiki);
                        section.setSectionType(SectionType.OVERVIEW);
                        section.setTitle("Section " + i);
                        section.setContent("Contains keyword: " + keyword);
                        section.setOrderIndex(i);
                        wiki.getSections().add(section);
                    }

                    FileExplanation file = new FileExplanation();
                    file.setWiki(wiki);
                    file.setFilePath("src/main/Main.java");
                    file.setLanguage("Java");
                    file.setExplanation("Contains keyword: " + keyword);
                    wiki.getFileExplanations().add(file);
                    return wiki;
                });
    }

    @Provide
    Arbitrary<String> repositoryNames() {
        return Arbitraries.of("test-repo", "sample-project", "demo-app", "example-service", "my-library");
    }

    @Provide
    Arbitrary<String> searchKeywords() {
        return Arbitraries.of("authentication", "database", "service", "controller", "repository", "testing");
    }

    @Provide
    Arbitrary<String> emptyOrNullStrings() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }
}
