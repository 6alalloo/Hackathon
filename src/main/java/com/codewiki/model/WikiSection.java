package com.codewiki.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "wiki_sections")
public class WikiSection {
    
    @Id
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wiki_id", nullable = false)
    private Wiki wiki;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SectionType sectionType;
    
    @Column(nullable = false)
    private String title;
    
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(nullable = false)
    private int orderIndex;
    
    public WikiSection() {
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
    
    public SectionType getSectionType() {
        return sectionType;
    }
    
    public void setSectionType(SectionType sectionType) {
        this.sectionType = sectionType;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public int getOrderIndex() {
        return orderIndex;
    }
    
    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
