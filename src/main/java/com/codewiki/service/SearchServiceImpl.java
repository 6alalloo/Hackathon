package com.codewiki.service;

import com.codewiki.dto.SearchResult;
import com.codewiki.model.FileExplanation;
import com.codewiki.model.Wiki;
import com.codewiki.model.WikiSection;
import com.codewiki.repository.WikiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Portable search service implementation.
 * Uses in-memory ranking over persisted wiki content to avoid DB-specific full-text dependencies.
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final int SNIPPET_CONTEXT_LENGTH = 150;

    private final WikiRepository wikiRepository;

    public SearchServiceImpl(WikiRepository wikiRepository) {
        this.wikiRepository = wikiRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedQuery = query.trim().toLowerCase();
        List<SearchResult> results = new ArrayList<>();

        for (Wiki wiki : wikiRepository.findAll()) {
            results.addAll(searchWikiSections(wiki, query, normalizedQuery));
            results.addAll(searchFileExplanations(wiki, query, normalizedQuery));
        }

        results.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        return results;
    }

    private List<SearchResult> searchWikiSections(Wiki wiki, String rawQuery, String normalizedQuery) {
        List<SearchResult> results = new ArrayList<>();

        for (WikiSection section : wiki.getSections()) {
            String title = nullSafe(section.getTitle());
            String content = nullSafe(section.getContent());
            String haystack = (title + " " + content).toLowerCase();

            double score = calculateRelevanceScore(haystack, title.toLowerCase(), normalizedQuery);
            if (score <= 0) {
                continue;
            }

            String snippet = extractSnippet(content, rawQuery);
            results.add(new SearchResult(
                    wiki.getId(),
                    wiki.getRepositoryName(),
                    section.getTitle(),
                    snippet,
                    score
            ));
        }

        return results;
    }

    private List<SearchResult> searchFileExplanations(Wiki wiki, String rawQuery, String normalizedQuery) {
        List<SearchResult> results = new ArrayList<>();

        for (FileExplanation file : wiki.getFileExplanations()) {
            String filePath = nullSafe(file.getFilePath());
            String explanation = nullSafe(file.getExplanation());
            String haystack = (filePath + " " + explanation).toLowerCase();

            double score = calculateRelevanceScore(haystack, filePath.toLowerCase(), normalizedQuery);
            if (score <= 0) {
                continue;
            }

            String snippet = extractSnippet(explanation, rawQuery);
            results.add(new SearchResult(
                    wiki.getId(),
                    wiki.getRepositoryName(),
                    "File: " + file.getFilePath(),
                    snippet,
                    score
            ));
        }

        return results;
    }

    private double calculateRelevanceScore(String haystack, String titleOrPath, String query) {
        if (!haystack.contains(query)) {
            return 0;
        }

        int occurrences = countOccurrences(haystack, query);
        double score = occurrences;

        if (titleOrPath.contains(query)) {
            score += 2.0;
        }

        return score;
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += Math.max(1, needle.length());
        }
        return count;
    }

    private String extractSnippet(String text, String query) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        int queryIndex = text.toLowerCase().indexOf(query.toLowerCase());
        if (queryIndex == -1) {
            return text.length() > SNIPPET_CONTEXT_LENGTH * 2
                    ? text.substring(0, SNIPPET_CONTEXT_LENGTH * 2) + "..."
                    : text;
        }

        int start = Math.max(0, queryIndex - SNIPPET_CONTEXT_LENGTH);
        int end = Math.min(text.length(), queryIndex + query.length() + SNIPPET_CONTEXT_LENGTH);

        String snippet = text.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }
        return snippet.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void indexWiki(Wiki wiki) {
        logger.debug("Indexing no-op for portable in-memory search. wikiId={}", wiki.getId());
    }

    @Override
    public void reindexAll() {
        logger.debug("Reindexing no-op for portable in-memory search");
    }
}
