package com.codewiki.service;

import com.codewiki.dto.SearchResult;
import com.codewiki.model.*;
import com.codewiki.repository.WikiRepository;
import net.jqwik.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for SearchService using jqwik.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchServicePropertyTest {
    
    @Autowired
    private SearchService searchService;
    
    @Autowired
    private WikiRepository wikiRepository;
    
    /**
     * Feature: codewiki-generator, Property 18: Search Across All Wikis
     * For any search query submitted, the Search_Engine should search across all Wiki_Content
     * in the Wiki_Database (not just a subset).
     * 
     * Validates: Requirements 9.2
     */
    @Property(tries = 100)
    void searchAcrossAllWikis_SearchesEntireDatabase(
        @ForAll("searchableWikis") List<Wiki> wikis,
        @ForAll("searchKeywords") String keyword
    ) {
        // Save all wikis to database
        for (Wiki wiki : wikis) {
            wikiRepository.save(wiki);
        }
        
        // Perform search
        List<SearchResult> results = searchService.search(keyword);
        
        // Count how many wikis contain the keyword
        long wikisWithKeyword = wikis.stream()
            .filter(wiki -> wikiContainsKeyword(wiki, keyword))
            .count();
        
        // If any wiki contains the keyword, we should get results
        if (wikisWithKeyword > 0) {
            assertFalse(results.isEmpty(),
                "Search should return results when keyword exists in wikis");
            
            // Verify results come from different wikis (if multiple wikis match)
            long uniqueWikiIds = results.stream()
                .map(SearchResult::getWikiId)
                .distinct()
                .count();
            
            assertTrue(uniqueWikiIds <= wikisWithKeyword,
                "Results should come from wikis containing the keyword");
        }
        
        // Clean up
        for (Wiki wiki : wikis) {
            wikiRepository.delete(wiki);
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 19: Search Result Ranking
     * For any search query that returns multiple results, the results should be ordered
     * by relevance score (descending).
     * 
     * Validates: Requirements 9.3
     */
    @Property(tries = 100)
    void searchResultRanking_OrdersByRelevanceDescending(
        @ForAll("searchableWikis") List<Wiki> wikis,
        @ForAll("searchKeywords") String keyword
    ) {
        // Save all wikis to database
        for (Wiki wiki : wikis) {
            wikiRepository.save(wiki);
        }
        
        // Perform search
        List<SearchResult> results = searchService.search(keyword);
        
        // If we have multiple results, verify they are sorted by relevance (descending)
        if (results.size() > 1) {
            for (int i = 0; i < results.size() - 1; i++) {
                double currentScore = results.get(i).getRelevanceScore();
                double nextScore = results.get(i + 1).getRelevanceScore();
                
                assertTrue(currentScore >= nextScore,
                    String.format("Results should be sorted by relevance (descending): " +
                        "result[%d].score=%.2f should be >= result[%d].score=%.2f",
                        i, currentScore, i + 1, nextScore));
            }
        }
        
        // Clean up
        for (Wiki wiki : wikis) {
            wikiRepository.delete(wiki);
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 20: Search Result Completeness
     * For any search result returned, it must include repository name, matching Wiki_Section
     * reference, and context snippet showing the match.
     * 
     * Validates: Requirements 9.4
     */
    @Property(tries = 100)
    void searchResultCompleteness_IncludesAllRequiredFields(
        @ForAll("searchableWikis") List<Wiki> wikis,
        @ForAll("searchKeywords") String keyword
    ) {
        // Save all wikis to database
        for (Wiki wiki : wikis) {
            wikiRepository.save(wiki);
        }
        
        // Perform search
        List<SearchResult> results = searchService.search(keyword);
        
        // Verify each result has all required fields
        for (SearchResult result : results) {
            assertNotNull(result.getWikiId(),
                "Search result must include wiki ID");
            
            assertNotNull(result.getRepositoryName(),
                "Search result must include repository name");
            assertFalse(result.getRepositoryName().trim().isEmpty(),
                "Repository name must not be empty");
            
            assertNotNull(result.getSectionReference(),
                "Search result must include section reference");
            assertFalse(result.getSectionReference().trim().isEmpty(),
                "Section reference must not be empty");
            
            assertNotNull(result.getSnippet(),
                "Search result must include context snippet");
            // Snippet can be empty for very short content, but should not be null
            
            assertTrue(result.getRelevanceScore() > 0,
                "Search result must have positive relevance score");
        }
        
        // Clean up
        for (Wiki wiki : wikis) {
            wikiRepository.delete(wiki);
        }
    }
    
    /**
     * Property: Empty Query Returns Empty Results
     * For any empty or null search query, the search should return an empty result list.
     */
    @Property(tries = 50)
    void emptyQuery_ReturnsEmptyResults(
        @ForAll("emptyOrNullStrings") String emptyQuery
    ) {
        List<SearchResult> results = searchService.search(emptyQuery);
        
        assertTrue(results.isEmpty(),
            "Empty or null query should return empty results");
    }
    
    /**
     * Property: Search Results Contain Query Keyword
     * For any non-empty query, all returned results should be relevant to the query.
     */
    @Property(tries = 100)
    void searchResults_AreRelevantToQuery(
        @ForAll("searchableWikis") List<Wiki> wikis,
        @ForAll("searchKeywords") String keyword
    ) {
        // Save all wikis to database
        for (Wiki wiki : wikis) {
            wikiRepository.save(wiki);
        }
        
        // Perform search
        List<SearchResult> results = searchService.search(keyword);
        
        // Each result should be from a wiki that contains the keyword
        for (SearchResult result : results) {
            Wiki wiki = wikiRepository.findById(result.getWikiId()).orElse(null);
            assertNotNull(wiki, "Result should reference an existing wiki");
            
            boolean wikiContainsKeyword = wikiContainsKeyword(wiki, keyword);
            assertTrue(wikiContainsKeyword,
                String.format("Wiki %s should contain keyword '%s'",
                    wiki.getRepositoryName(), keyword));
        }
        
        // Clean up
        for (Wiki wiki : wikis) {
            wikiRepository.delete(wiki);
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Check if a wiki contains the given keyword in any of its content.
     */
    private boolean wikiContainsKeyword(Wiki wiki, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        
        // Check sections
        for (WikiSection section : wiki.getSections()) {
            if (section.getTitle().toLowerCase().contains(lowerKeyword) ||
                section.getContent().toLowerCase().contains(lowerKeyword)) {
                return true;
            }
        }
        
        // Check file explanations
        for (FileExplanation file : wiki.getFileExplanations()) {
            if (file.getFilePath().toLowerCase().contains(lowerKeyword) ||
                file.getExplanation().toLowerCase().contains(lowerKeyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    // ========== Arbitraries (Generators) ==========
    
    /**
     * Generates a list of searchable wikis with content.
     */
    @Provide
    Arbitrary<List<Wiki>> searchableWikis() {
        return Arbitraries.integers().between(1, 5).flatMap(count -> {
            List<Arbitrary<Wiki>> wikiArbitraries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                wikiArbitraries.add(wikiWithContent());
            }
            return Combinators.combine(wikiArbitraries).as(wikis -> 
                wikis.stream().collect(Collectors.toList())
            );
        });
    }
    
    /**
     * Generates a single wiki with sections and file explanations.
     */
    private Arbitrary<Wiki> wikiWithContent() {
        return Combinators.combine(
            repositoryNames(),
            searchKeywords(),
            Arbitraries.integers().between(1, 3)
        ).as((repoName, keyword, sectionCount) -> {
            Wiki wiki = new Wiki();
            wiki.setRepositoryUrl("https://github.com/test/" + repoName);
            wiki.setRepositoryName(repoName);
            wiki.setStatus(WikiStatus.COMPLETED);
            
            // Add sections with keyword
            for (int i = 0; i < sectionCount; i++) {
                WikiSection section = new WikiSection();
                section.setWiki(wiki);
                section.setSectionType(SectionType.OVERVIEW);
                section.setTitle("Section " + i);
                section.setContent("This section contains the keyword: " + keyword + 
                    ". Additional content for testing search functionality.");
                section.setOrderIndex(i);
                wiki.getSections().add(section);
            }
            
            // Add file explanations
            FileExplanation file = new FileExplanation();
            file.setWiki(wiki);
            file.setFilePath("src/main/Main.java");
            file.setLanguage("Java");
            file.setExplanation("This file explanation contains: " + keyword);
            wiki.getFileExplanations().add(file);
            
            return wiki;
        });
    }
    
    /**
     * Generates repository names.
     */
    @Provide
    Arbitrary<String> repositoryNames() {
        return Arbitraries.of(
            "test-repo",
            "sample-project",
            "demo-app",
            "example-service",
            "my-library"
        );
    }
    
    /**
     * Generates search keywords.
     */
    @Provide
    Arbitrary<String> searchKeywords() {
        return Arbitraries.of(
            "authentication",
            "database",
            "service",
            "controller",
            "repository",
            "configuration",
            "security",
            "testing"
        );
    }
    
    /**
     * Generates empty or null strings.
     */
    @Provide
    Arbitrary<String> emptyOrNullStrings() {
        return Arbitraries.of(
            null,
            "",
            "   ",
            "\t",
            "\n"
        );
    }
}
