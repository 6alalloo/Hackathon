package com.codewiki.dto;

/**
 * DTO for wiki sections.
 */
public class WikiSectionDTO {
    
    private String id;
    private String sectionType;
    private String title;
    private String content;
    private int orderIndex;
    
    public WikiSectionDTO() {
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getSectionType() {
        return sectionType;
    }
    
    public void setSectionType(String sectionType) {
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
