package com.codewiki.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for chatbot answers.
 * Contains the answer text with hyperlinks and references to wiki sections.
 */
public class ChatResponse {
    
    private String answer;
    private List<String> references;
    
    public ChatResponse() {
        this.references = new ArrayList<>();
    }
    
    public ChatResponse(String answer, List<String> references) {
        this.answer = answer;
        this.references = references != null ? references : new ArrayList<>();
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public List<String> getReferences() {
        return references;
    }
    
    public void setReferences(List<String> references) {
        this.references = references;
    }
}
