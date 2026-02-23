package com.codewiki.service;

import com.codewiki.dto.SearchResult;
import com.codewiki.model.FileExplanation;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiSection;
import com.codewiki.repository.WikiRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of SearchService using H2's built-in full-text search (Apache Lucene).
 */
@Service
public class SearchServiceImpl implements SearchService {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final int SNIPPET_CONTEXT_LENGTH = 150;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final WikiRepository wikiRepository;
    
    public SearchServiceImpl(WikiRepository wikiRepository) {
        this.wikiRepository = wikiRepository;
    }
    
    @PostConstruct
    @Transactional
    public void initializeFullTextSearch() {
        try {
            // Create full-text indexes for wiki sections
            entityManager.createNativeQuery(
                "CREATE ALIAS IF NOT EXISTS FT_INIT FOR \"org.h2.fulltext.FullText.init\""
            ).executeUpdate();
            
            entityManager.createNativeQuery("CALL FT_INIT()").executeUpdate();
            
            // Initialize full-text search on wiki_sections table
            entityManager.createNativeQuery(
                "CALL FT_CREATE_INDEX('PUBLIC', 'WIKI_SECTIONS', 'TITLE,CONTENT')"
            ).executeUpdate();
            
            // Initialize full-text search on file_explanations table
            entityManager.createNativeQuery(
                "CALL FT_CREATE_INDEX('PUBLIC', 'FILE_EXPLANATIONS', 'FILE_PATH,EXPLANATION')"
            ).executeUpdate();
            
            logger.info("Full-text search indexes initialized successfully");
        } catch (Exception e) {
            // Index might already exist, log and continue
            logger.warn("Full-text search initialization: {}", e.getMessage());
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<SearchResult> results = new ArrayList<>();
        
        // Search in wiki sections
        results.addAll(searchWikiSections(query));
        
        // Search in file explanations
        results.addAll(searchFileExplanations(query));
        
        // Sort by relevance score (descending)
        results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        
        return results;
    }
    
    @SuppressWarnings("unchecked")
    private List<SearchResult> searchWikiSections(String query) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            // Use H2 full-text search with scoring
            String sql = "SELECT WS.ID, WS.WIKI_ID, WS.TITLE, WS.CONTENT, FT.SCORE " +
                        "FROM WIKI_SECTIONS WS " +
                        "JOIN FT_SEARCH_DATA(?, 0, 0) FT ON FT.TABLE='WIKI_SECTIONS' AND FT.KEYS[0]=WS.ID " +
                        "ORDER BY FT.SCORE DESC";
            
            Query nativeQuery = entityManager.createNativeQuery(sql);
            nativeQuery.setParameter(1, query);
            
            List<Object[]> queryResults = nativeQuery.getResultList();
            
            for (Object[] row : queryResults) {
                String sectionId = (String) row[0];
                String wikiId = (String) row[1];
                String title = (String) row[2];
                String content = (String) row[3];
                double score = ((Number) row[4]).doubleValue();
                
                // Get wiki information
                Wiki wiki = wikiRepository.findById(wikiId).orElse(null);
                if (wiki == null) {
                    continue;
                }
                
                // Extract snippet with context
                String snippet = extractSnippet(content, query);
                
                // Boost score if query matches title
                if (title.toLowerCase().contains(query.toLowerCase())) {
                    score *= 1.5;
                }
                
                SearchResult result = new SearchResult(
                    wikiId,
                    wiki.getRepositoryName(),
                    title,
                    snippet,
                    score
                );
                
                results.add(result);
            }
        } catch (Exception e) {
            logger.error("Error searching wiki sections: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    @SuppressWarnings("unchecked")
    private List<SearchResult> searchFileExplanations(String query) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            // Use H2 full-text search with scoring
            String sql = "SELECT FE.ID, FE.WIKI_ID, FE.FILE_PATH, FE.EXPLANATION, FT.SCORE " +
                        "FROM FILE_EXPLANATIONS FE " +
                        "JOIN FT_SEARCH_DATA(?, 0, 0) FT ON FT.TABLE='FILE_EXPLANATIONS' AND FT.KEYS[0]=FE.ID " +
                        "ORDER BY FT.SCORE DESC";
            
            Query nativeQuery = entityManager.createNativeQuery(sql);
            nativeQuery.setParameter(1, query);
            
            List<Object[]> queryResults = nativeQuery.getResultList();
            
            for (Object[] row : queryResults) {
                String fileId = (String) row[0];
                String wikiId = (String) row[1];
                String filePath = (String) row[2];
                String explanation = (String) row[3];
                double score = ((Number) row[4]).doubleValue();
                
                // Get wiki information
                Wiki wiki = wikiRepository.findById(wikiId).orElse(null);
                if (wiki == null) {
                    continue;
                }
                
                // Extract snippet with context
                String snippet = extractSnippet(explanation, query);
                
                SearchResult result = new SearchResult(
                    wikiId,
                    wiki.getRepositoryName(),
                    "File: " + filePath,
                    snippet,
                    score
                );
                
                results.add(result);
            }
        } catch (Exception e) {
            logger.error("Error searching file explanations: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * Extract a snippet of text around the first occurrence of the query.
     * 
     * @param text The full text content
     * @param query The search query
     * @return A snippet with context around the match
     */
    private String extractSnippet(String text, String query) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // Find the first occurrence of the query (case-insensitive)
        int queryIndex = text.toLowerCase().indexOf(query.toLowerCase());
        
        if (queryIndex == -1) {
            // Query not found, return beginning of text
            return text.length() > SNIPPET_CONTEXT_LENGTH * 2 
                ? text.substring(0, SNIPPET_CONTEXT_LENGTH * 2) + "..."
                : text;
        }
        
        // Calculate snippet boundaries
        int start = Math.max(0, queryIndex - SNIPPET_CONTEXT_LENGTH);
        int end = Math.min(text.length(), queryIndex + query.length() + SNIPPET_CONTEXT_LENGTH);
        
        // Extract snippet
        String snippet = text.substring(start, end);
        
        // Add ellipsis if truncated
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }
        
        return snippet.trim();
    }
    
    @Override
    @Transactional
    public void indexWiki(Wiki wiki) {
        // H2 full-text search automatically indexes on INSERT/UPDATE
        // This method is a no-op but kept for interface compatibility
        logger.debug("Wiki {} indexed automatically by H2 full-text search", wiki.getId());
    }
    
    @Override
    @Transactional
    public void reindexAll() {
        try {
            // Drop and recreate indexes
            entityManager.createNativeQuery("CALL FT_DROP_ALL()").executeUpdate();
            
            // Reinitialize
            initializeFullTextSearch();
            
            logger.info("All wikis reindexed successfully");
        } catch (Exception e) {
            logger.error("Error reindexing all wikis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reindex wikis", e);
        }
    }
}
