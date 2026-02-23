package com.codewiki.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for wiki retrieval.
 */
public class WikiResponse {
    
    private String id;
    private String repositoryUrl;
    private String repositoryName;
    private String lastCommitHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean stale;
    private String status;
    private List<WikiSectionDTO> sections;
    private List<FileExplanationDTO> fileExplanations;
    
    public WikiResponse() {
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
    
    public String getRepositoryName() {
        return repositoryName;
    }
    
    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }
    
    public String getLastCommitHash() {
        return lastCommitHash;
    }
    
    public void setLastCommitHash(String lastCommitHash) {
        this.lastCommitHash = lastCommitHash;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isStale() {
        return stale;
    }
    
    public void setStale(boolean stale) {
        this.stale = stale;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public List<WikiSectionDTO> getSections() {
        return sections;
    }
    
    public void setSections(List<WikiSectionDTO> sections) {
        this.sections = sections;
    }
    
    public List<FileExplanationDTO> getFileExplanations() {
        return fileExplanations;
    }
    
    public void setFileExplanations(List<FileExplanationDTO> fileExplanations) {
        this.fileExplanations = fileExplanations;
    }
}
