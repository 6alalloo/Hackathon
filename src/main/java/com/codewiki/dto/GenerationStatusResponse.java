package com.codewiki.dto;

import com.codewiki.model.GenerationPhase;
import com.codewiki.model.GenerationState;

/**
 * Response DTO for generation status polling.
 */
public class GenerationStatusResponse {
    
    private GenerationState status;
    private GenerationPhase phase;
    private String errorMessage;
    
    public GenerationStatusResponse() {
    }
    
    public GenerationStatusResponse(GenerationState status, GenerationPhase phase, String errorMessage) {
        this.status = status;
        this.phase = phase;
        this.errorMessage = errorMessage;
    }
    
    public GenerationState getStatus() {
        return status;
    }
    
    public void setStatus(GenerationState status) {
        this.status = status;
    }
    
    public GenerationPhase getPhase() {
        return phase;
    }
    
    public void setPhase(GenerationPhase phase) {
        this.phase = phase;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
