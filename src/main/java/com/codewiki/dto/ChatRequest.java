package com.codewiki.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for chatbot questions.
 */
public class ChatRequest {
    
    @NotBlank(message = "question is required")
    private String question;
    
    public ChatRequest() {
    }
    
    public ChatRequest(String question) {
        this.question = question;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
}
