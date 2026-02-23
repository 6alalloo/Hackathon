package com.codewiki.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "generation_status")
public class GenerationStatus {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String wikiId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationPhase phase;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationState status;
    
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
    
    public GenerationPhase getPhase() {
        return phase;
    }
    
    public void setPhase(GenerationPhase phase) {
        this.phase = phase;
    }
    
    public GenerationState getStatus() {
        return status;
    }
    
    public void setStatus(GenerationState status) {
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
    
    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
