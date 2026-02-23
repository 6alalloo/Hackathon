package com.codewiki.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for submitting a repository URL to generate a wiki.
 */
public class WikiSubmissionRequest {
    
    @NotBlank(message = "repositoryUrl is required")
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
