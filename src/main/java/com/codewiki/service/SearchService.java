package com.codewiki.service;

import com.codewiki.dto.SearchResult;
import com.codewiki.model.Wiki;

import java.util.List;

/**
 * Service interface for full-text search across wiki content.
 */
public interface SearchService {
    
    /**
     * Search across all wikis for the given query.
     * 
     * @param query The search query string
     * @return List of search results ranked by relevance
     */
    List<SearchResult> search(String query);
    
    /**
     * Index a wiki for full-text search.
     * 
     * @param wiki The wiki to index
     */
    void indexWiki(Wiki wiki);
    
    /**
     * Reindex all wikis in the database.
     */
    void reindexAll();
}
