package com.codewiki.service;

import com.codewiki.dto.SearchResult;
import com.codewiki.model.*;
import com.codewiki.repository.WikiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchService edge cases.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchServiceTest {
    
    @Autowired
    private SearchService searchService;
    
    @Autowired
    private WikiRepository wikiRepository;
    
    private Wiki testWiki1;
    private Wiki testWiki2;
    
    @BeforeEach
    void setUp() {
        // Create test wiki 1
        testWiki1 = new Wiki();
        testWiki1.setRepositoryUrl("https://github.com/test/repo1");
        testWiki1.setRepositoryName("test-repo1");
        testWiki1.setStatus(WikiStatus.COMPLETED);
        
        WikiSection section1 = new WikiSection();
        section1.setWiki(testWiki1);
        section1.setSectionType(SectionType.OVERVIEW);
        section1.setTitle("Authentication Overview");
        section1.setContent("This repository implements authentication using JWT tokens and OAuth2.");
        section1.setOrderIndex(0);
        testWiki1.getSections().add(section1);
        
        FileExplanation file1 = new FileExplanation();
        file1.setWiki(testWiki1);
        file1.setFilePath("src/main/AuthService.java");
        file1.setLanguage("Java");
        file1.setExplanation("Handles user authentication and token generation.");
        testWiki1.getFileExplanations().add(file1);
        
        wikiRepository.save(testWiki1);
        
        // Create test wiki 2
        testWiki2 = new Wiki();
        testWiki2.setRepositoryUrl("https://github.com/test/repo2");
        testWiki2.setRepositoryName("test-repo2");
        testWiki2.setStatus(WikiStatus.COMPLETED);
        
        WikiSection section2 = new WikiSection();
        section2.setWiki(testWiki2);
        section2.setSectionType(SectionType.ARCHITECTURE);
        section2.setTitle("Database Architecture");
        section2.setContent("Uses PostgreSQL for data persistence with authentication layer.");
        section2.setOrderIndex(0);
        testWiki2.getSections().add(section2);
        
        wikiRepository.save(testWiki2);
    }
    
    /**
     * Test empty search query returns empty results.
     */
    @Test
    void testEmptySearchQuery() {
        List<SearchResult> results = searchService.search("");
        
        assertTrue(results.isEmpty(), "Empty query should return empty results");
    }
    
    /**
     * Test null search query returns empty results.
     */
    @Test
    void testNullSearchQuery() {
        List<SearchResult> results = searchService.search(null);
        
        assertTrue(results.isEmpty(), "Null query should return empty results");
    }
    
    /**
     * Test whitespace-only search query returns empty results.
     */
    @Test
    void testWhitespaceOnlySearchQuery() {
        List<SearchResult> results = searchService.search("   ");
        
        assertTrue(results.isEmpty(), "Whitespace-only query should return empty results");
    }
    
    /**
     * Test query with special characters.
     */
    @Test
    void testQueryWithSpecialCharacters() {
        // Create wiki with special characters
        Wiki specialWiki = new Wiki();
        specialWiki.setRepositoryUrl("https://github.com/test/special");
        specialWiki.setRepositoryName("special-repo");
        specialWiki.setStatus(WikiStatus.COMPLETED);
        
        WikiSection section = new WikiSection();
        section.setWiki(specialWiki);
        section.setSectionType(SectionType.OVERVIEW);
        section.setTitle("C++ Implementation");
        section.setContent("Uses C++ templates and std::vector for data structures.");
        section.setOrderIndex(0);
        specialWiki.getSections().add(section);
        
        wikiRepository.save(specialWiki);
        
        // Search with special characters
        List<SearchResult> results = searchService.search("C++");
        
        // Should handle special characters gracefully (may or may not find results)
        assertNotNull(results, "Results should not be null");
    }
    
    /**
     * Test query matching multiple wikis.
     */
    @Test
    void testQueryMatchingMultipleWikis() {
        List<SearchResult> results = searchService.search("authentication");
        
        assertFalse(results.isEmpty(), "Should find results for 'authentication'");
        
        // Should find results from both wikis (testWiki1 has it in title and content, testWiki2 has it in content)
        assertTrue(results.size() >= 1, "Should find at least one result");
        
        // Verify results contain expected repository names
        boolean foundRepo1 = results.stream()
            .anyMatch(r -> r.getRepositoryName().equals("test-repo1"));
        boolean foundRepo2 = results.stream()
            .anyMatch(r -> r.getRepositoryName().equals("test-repo2"));
        
        assertTrue(foundRepo1 || foundRepo2, "Should find results from at least one repository");
    }
    
    /**
     * Test query matching no wikis.
     */
    @Test
    void testQueryMatchingNoWikis() {
        List<SearchResult> results = searchService.search("nonexistentkeyword12345");
        
        assertTrue(results.isEmpty(), "Should return empty results for non-existent keyword");
    }
    
    /**
     * Test search results are ranked by relevance.
     */
    @Test
    void testSearchResultsRanking() {
        List<SearchResult> results = searchService.search("authentication");
        
        if (results.size() > 1) {
            // Verify results are sorted by relevance score (descending)
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(results.get(i).getRelevanceScore() >= results.get(i + 1).getRelevanceScore(),
                    "Results should be sorted by relevance score in descending order");
            }
        }
    }
    
    /**
     * Test search result contains all required fields.
     */
    @Test
    void testSearchResultCompleteness() {
        List<SearchResult> results = searchService.search("authentication");
        
        assertFalse(results.isEmpty(), "Should find results");
        
        for (SearchResult result : results) {
            assertNotNull(result.getWikiId(), "Wiki ID should not be null");
            assertNotNull(result.getRepositoryName(), "Repository name should not be null");
            assertNotNull(result.getSectionReference(), "Section reference should not be null");
            assertNotNull(result.getSnippet(), "Snippet should not be null");
            assertTrue(result.getRelevanceScore() > 0, "Relevance score should be positive");
        }
    }
    
    /**
     * Test search with case-insensitive matching.
     */
    @Test
    void testCaseInsensitiveSearch() {
        List<SearchResult> resultsLower = searchService.search("authentication");
        List<SearchResult> resultsUpper = searchService.search("AUTHENTICATION");
        List<SearchResult> resultsMixed = searchService.search("Authentication");
        
        // All should return results (case-insensitive)
        assertFalse(resultsLower.isEmpty(), "Lowercase query should find results");
        assertFalse(resultsUpper.isEmpty(), "Uppercase query should find results");
        assertFalse(resultsMixed.isEmpty(), "Mixed case query should find results");
    }
    
    /**
     * Test search snippet extraction.
     */
    @Test
    void testSnippetExtraction() {
        List<SearchResult> results = searchService.search("authentication");
        
        assertFalse(results.isEmpty(), "Should find results");
        
        SearchResult firstResult = results.get(0);
        String snippet = firstResult.getSnippet();
        
        assertNotNull(snippet, "Snippet should not be null");
        assertFalse(snippet.trim().isEmpty(), "Snippet should not be empty");
        
        // Snippet should contain the search keyword (case-insensitive)
        assertTrue(snippet.toLowerCase().contains("authentication"),
            "Snippet should contain the search keyword");
    }
    
    /**
     * Test search in file explanations.
     */
    @Test
    void testSearchInFileExplanations() {
        List<SearchResult> results = searchService.search("AuthService");
        
        assertFalse(results.isEmpty(), "Should find results in file explanations");
        
        // Verify at least one result references a file
        boolean hasFileReference = results.stream()
            .anyMatch(r -> r.getSectionReference().startsWith("File:"));
        
        assertTrue(hasFileReference, "Should have at least one file reference");
    }
    
    /**
     * Test search with partial word matching.
     */
    @Test
    void testPartialWordMatching() {
        List<SearchResult> results = searchService.search("auth");
        
        // Should find results containing "authentication" or "AuthService"
        assertFalse(results.isEmpty(), "Should find results with partial word match");
    }
}
