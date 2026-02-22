package com.codewiki.service;

import com.codewiki.model.CodeFile;
import com.codewiki.model.ValidationResult;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.nio.file.Path;
import java.util.List;

/**
 * Service interface for GitHub repository operations.
 * Handles validation, cloning, size checking, and code file detection.
 */
public interface RepositoryService {
    
    /**
     * Validates a GitHub repository URL.
     * Checks if the URL matches the format: https://github.com/{owner}/{repo}
     * 
     * @param url the repository URL to validate
     * @return ValidationResult indicating success or failure with error message
     */
    ValidationResult validateRepositoryUrl(String url);
    
    /**
     * Gets the size of a GitHub repository using the GitHub API.
     * Checks the repository size before cloning to enforce size limits.
     * 
     * @param url the repository URL
     * @return the repository size in bytes
     * @throws GitAPIException if the repository size exceeds the maximum allowed size (10MB)
     *                         or if the GitHub API call fails
     */
    long getRepositorySize(String url) throws GitAPIException;
    
    /**
     * Clones a GitHub repository to local storage.
     * Uses JGit to clone the repository to the configured base path.
     * 
     * @param url the repository URL to clone
     * @return the Path to the cloned repository
     * @throws GitAPIException if cloning fails
     */
    Path cloneRepository(String url) throws GitAPIException;
    
    /**
     * Detects code files in a cloned repository.
     * Scans the repository for files with supported programming language extensions.
     * 
     * @param repoPath the path to the cloned repository
     * @return list of CodeFile objects containing file paths and detected languages
     */
    List<CodeFile> detectCodeFiles(Path repoPath);
    
    /**
     * Cleans up a cloned repository by deleting it from local storage.
     * Used to remove temporary repository files after processing or on failure.
     * 
     * @param repoPath the path to the repository to clean up
     */
    void cleanupRepository(Path repoPath);
}
