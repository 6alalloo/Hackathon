package com.codewiki.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "wikis")
public class Wiki {
    
    @Id
    private String id;
    
    @Column(unique = true, nullable = false)
    private String repositoryUrl;
    
    @Column(nullable = false)
    private String repositoryName;
    
    private String lastCommitHash;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(nullable = false)
    private boolean stale;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WikiStatus status;
    
    @OneToMany(mappedBy = "wiki", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WikiSection> sections = new ArrayList<>();
    
    @OneToMany(mappedBy = "wiki", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileExplanation> fileExplanations = new ArrayList<>();
    
    @OneToMany(mappedBy = "wiki", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> chatMessages = new ArrayList<>();
    
    public Wiki() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.stale = false;
        this.status = WikiStatus.PENDING;
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
    
    public WikiStatus getStatus() {
        return status;
    }
    
    public void setStatus(WikiStatus status) {
        this.status = status;
    }
    
    public List<WikiSection> getSections() {
        return sections;
    }
    
    public void setSections(List<WikiSection> sections) {
        this.sections = sections;
    }
    
    public List<FileExplanation> getFileExplanations() {
        return fileExplanations;
    }
    
    public void setFileExplanations(List<FileExplanation> fileExplanations) {
        this.fileExplanations = fileExplanations;
    }
    
    public List<ChatMessage> getChatMessages() {
        return chatMessages;
    }
    
    public void setChatMessages(List<ChatMessage> chatMessages) {
        this.chatMessages = chatMessages;
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
