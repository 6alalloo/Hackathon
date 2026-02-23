package com.codewiki.controller;

import com.codewiki.dto.SearchResult;
import com.codewiki.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for search functionality.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    
    private final SearchService searchService;
    
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }
    
    /**
     * Search across all wikis.
     * 
     * @param query The search query string
     * @return List of search results ranked by relevance
     */
    @GetMapping
    public ResponseEntity<List<SearchResult>> search(@RequestParam("q") String query) {
        logger.info("Search request received: query={}", query);
        
        List<SearchResult> results = searchService.search(query);
        
        logger.info("Search completed: query={}, results={}", query, results.size());
        
        return ResponseEntity.ok(results);
    }
}
