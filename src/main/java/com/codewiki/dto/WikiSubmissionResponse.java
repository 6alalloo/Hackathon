package com.codewiki.dto;

/**
 * Response DTO for wiki submission.
 */
public class WikiSubmissionResponse {
    
    private String wikiId;
    private String status;
    
    public WikiSubmissionResponse() {
    }
    
    public WikiSubmissionResponse(String wikiId, String status) {
        this.wikiId = wikiId;
        this.status = status;
    }
    
    public String getWikiId() {
        return wikiId;
    }
    
    public void setWikiId(String wikiId) {
        this.wikiId = wikiId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
}
