package com.codewiki.dto;

/**
 * Response DTO for generation status polling.
 */
public class GenerationStatusResponse {
    
    private String status;
    private String phase;
    private String errorMessage;
    
    public GenerationStatusResponse() {
    }
    
    public GenerationStatusResponse(String status, String phase, String errorMessage) {
        this.status = status;
        this.phase = phase;
        this.errorMessage = errorMessage;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getPhase() {
        return phase;
    }
    
    public void setPhase(String phase) {
        this.phase = phase;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
