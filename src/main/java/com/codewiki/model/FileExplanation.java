package com.codewiki.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "file_explanations")
public class FileExplanation {
    
    @Id
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wiki_id", nullable = false)
    private Wiki wiki;
    
    @Column(nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private String language;
    
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String codeSnippet;
    
    public FileExplanation() {
        this.id = UUID.randomUUID().toString();
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Wiki getWiki() {
        return wiki;
    }
    
    public void setWiki(Wiki wiki) {
        this.wiki = wiki;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
    
    public String getCodeSnippet() {
        return codeSnippet;
    }
    
    public void setCodeSnippet(String codeSnippet) {
        this.codeSnippet = codeSnippet;
    }
}
