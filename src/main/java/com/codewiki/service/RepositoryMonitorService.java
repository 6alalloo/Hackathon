package com.codewiki.service;

/**
 * Service interface for repository monitoring operations.
 * Detects updates to GitHub repositories and marks wikis as stale.
 */
public interface RepositoryMonitorService {
    
    /**
     * Checks all wikis for repository updates.
     * Scheduled task that runs every 24 hours.
     * Compares stored commit hash with remote commit hash.
     * Marks wikis as stale when updates are detected.
     */
    void checkForUpdates();
    
    /**
     * Checks if a specific repository has been updated.
     * Fetches the latest commit hash from GitHub and compares with stored hash.
     * 
     * @param repositoryUrl the GitHub repository URL
     * @param lastCommitHash the stored commit hash
     * @return true if the repository has been updated, false otherwise
     */
    boolean isRepositoryUpdated(String repositoryUrl, String lastCommitHash);
    
    /**
     * Marks a wiki as stale.
     * Sets the stale flag to true in the database.
     * 
     * @param wikiId the wiki ID
     */
    void markWikiAsStale(String wikiId);
}
