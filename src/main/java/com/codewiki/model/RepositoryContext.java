package com.codewiki.model;

import java.util.List;
import java.util.Map;

/**
 * Context object containing repository metadata for LLM prompts.
 * Includes file structure, languages, entry points, and code files.
 */
public class RepositoryContext {
    
    private String repositoryUrl;
    private String repositoryName;
    private String commitHash;
    private List<CodeFile> codeFiles;
    private Map<String, Integer> languageDistribution;
    private List<String> entryPoints;
    private String fileStructure;
    private int totalFiles;
    
    public RepositoryContext() {
    }
    
    // Getters and setters
    
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
    
    public String getCommitHash() {
        return commitHash;
    }
    
    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }
    
    public List<CodeFile> getCodeFiles() {
        return codeFiles;
    }
    
    public void setCodeFiles(List<CodeFile> codeFiles) {
        this.codeFiles = codeFiles;
    }
    
    public Map<String, Integer> getLanguageDistribution() {
        return languageDistribution;
    }
    
    public void setLanguageDistribution(Map<String, Integer> languageDistribution) {
        this.languageDistribution = languageDistribution;
    }
    
    public List<String> getEntryPoints() {
        return entryPoints;
    }
    
    public void setEntryPoints(List<String> entryPoints) {
        this.entryPoints = entryPoints;
    }
    
    public String getFileStructure() {
        return fileStructure;
    }
    
    public void setFileStructure(String fileStructure) {
        this.fileStructure = fileStructure;
    }
    
    public int getTotalFiles() {
        return totalFiles;
    }
    
    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }
}
