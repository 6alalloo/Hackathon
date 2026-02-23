package com.codewiki.dto;

/**
 * DTO representing a single search result.
 */
public class SearchResult {
    
    private String wikiId;
    private String repositoryName;
    private String sectionReference;
    private String snippet;
    private double relevanceScore;
    
    public SearchResult() {
    }
    
    public SearchResult(String wikiId, String repositoryName, String sectionReference, 
                       String snippet, double relevanceScore) {
        this.wikiId = wikiId;
        this.repositoryName = repositoryName;
        this.sectionReference = sectionReference;
        this.snippet = snippet;
        this.relevanceScore = relevanceScore;
    }
    
    // Getters and setters
    
    public String getWikiId() {
        return wikiId;
    }
    
    public void setWikiId(String wikiId) {
        this.wikiId = wikiId;
    }
    
    public String getRepositoryName() {
        return repositoryName;
    }
    
    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }
    
    public String getSectionReference() {
        return sectionReference;
    }
    
    public void setSectionReference(String sectionReference) {
        this.sectionReference = sectionReference;
    }
    
    public String getSnippet() {
        return snippet;
    }
    
    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
    
    public double getRelevanceScore() {
        return relevanceScore;
    }
    
    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
