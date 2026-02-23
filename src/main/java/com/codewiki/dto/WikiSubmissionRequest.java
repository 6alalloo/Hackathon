package com.codewiki.dto;

/**
 * Request DTO for submitting a repository URL to generate a wiki.
 */
public class WikiSubmissionRequest {
    
    private String repositoryUrl;
    
    public WikiSubmissionRequest() {
    }
    
    public WikiSubmissionRequest(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}
