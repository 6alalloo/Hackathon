package com.codewiki.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generation_status")
public class GenerationStatus {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String wikiId;
    
    @Column(nullable = false)
    private String phase;
    
    @Column(nullable = false)
    private String status;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    public GenerationStatus() {
        this.id = UUID.randomUUID().toString();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getWikiId() {
        return wikiId;
    }
    
    public void setWikiId(String wikiId) {
        this.wikiId = wikiId;
    }
    
    public String getPhase() {
        return phase;
    }
    
    public void setPhase(String phase) {
        this.phase = phase;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
