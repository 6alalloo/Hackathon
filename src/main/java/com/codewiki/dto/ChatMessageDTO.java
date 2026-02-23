package com.codewiki.dto;

import com.codewiki.model.MessageRole;
import java.time.LocalDateTime;

/**
 * DTO for chat message history responses.
 * Avoids lazy loading issues with Wiki entity.
 */
public class ChatMessageDTO {
    
    private String id;
    private MessageRole role;
    private String content;
    private LocalDateTime createdAt;
    
    public ChatMessageDTO() {
    }
    
    public ChatMessageDTO(String id, MessageRole role, String content, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public MessageRole getRole() {
        return role;
    }
    
    public void setRole(MessageRole role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
