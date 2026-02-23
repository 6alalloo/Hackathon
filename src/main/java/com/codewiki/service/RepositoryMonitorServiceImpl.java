package com.codewiki.service;

import com.codewiki.model.Wiki;
import com.codewiki.repository.WikiRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of RepositoryMonitorService for repository update detection.
 * Uses scheduled tasks to periodically check for repository updates.
 */
@Service
public class RepositoryMonitorServiceImpl implements RepositoryMonitorService {
    
    private static final Logger logger = LoggerFactory.getLogger(RepositoryMonitorServiceImpl.class);
    
    // Pattern for GitHub repository URLs
    private static final Pattern GITHUB_URL_PATTERN = 
        Pattern.compile("^https://github\\.com/([a-zA-Z0-9_-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?$");
    
    @Autowired
    private WikiRepository wikiRepository;
    
    private final WebClient webClient;
    
    public RepositoryMonitorServiceImpl() {
        this.webClient = WebClient.builder()
            .baseUrl("https://api.github.com")
            .build();
    }
    
    /**
     * Scheduled task that runs every 24 hours to check for repository updates.
     * Cron expression: "0 0 0 * * ?" = every day at midnight
     */
    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    public void checkForUpdates() {
        logger.info("Starting scheduled repository update check");
        
        try {
            // Get all completed wikis
            List<Wiki> wikis = wikiRepository.findAll();
            int checkedCount = 0;
            int updatedCount = 0;
            
            for (Wiki wiki : wikis) {
                // Only check completed wikis with a commit hash
                if (wiki.getLastCommitHash() != null && !wiki.getLastCommitHash().isEmpty()) {
                    try {
                        logger.debug("Checking for updates: {} (wikiId: {})", 
                            wiki.getRepositoryUrl(), wiki.getId());
                        
                        boolean isUpdated = isRepositoryUpdated(
                            wiki.getRepositoryUrl(), 
                            wiki.getLastCommitHash()
                        );
                        
                        if (isUpdated) {
                            logger.info("Update detected for repository: {} (wikiId: {})", 
                                wiki.getRepositoryUrl(), wiki.getId());
                            markWikiAsStale(wiki.getId());
                            updatedCount++;
                        }
                        
                        checkedCount++;
                        
                    } catch (Exception e) {
                        logger.warn("Failed to check updates for wiki {}: {}", 
                            wiki.getId(), e.getMessage());
                        // Continue checking other wikis
                    }
                }
            }
            
            logger.info("Repository update check completed. Checked: {}, Updated: {}", 
                checkedCount, updatedCount);
            
        } catch (Exception e) {
            logger.error("Error during scheduled repository update check", e);
        }
    }
    
    @Override
    public boolean isRepositoryUpdated(String repositoryUrl, String lastCommitHash) {
        logger.debug("Checking if repository is updated: {}", repositoryUrl);
        
        if (repositoryUrl == null || lastCommitHash == null) {
            logger.warn("Invalid parameters: repositoryUrl or lastCommitHash is null");
            return false;
        }
        
        try {
            // Extract owner and repo from URL
            Matcher matcher = GITHUB_URL_PATTERN.matcher(repositoryUrl);
            if (!matcher.matches()) {
                logger.warn("Invalid GitHub URL format: {}", repositoryUrl);
                return false;
            }
            
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            
            // Call GitHub API to get latest commit on default branch
            String apiPath = String.format("/repos/%s/%s/commits/HEAD", owner, repo);
            logger.debug("Calling GitHub API: {}", apiPath);
            
            Map<String, Object> response = webClient.get()
                .uri(apiPath)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response == null || !response.containsKey("sha")) {
                logger.warn("Invalid response from GitHub API for repository: {}", repositoryUrl);
                return false;
            }
            
            String remoteCommitHash = (String) response.get("sha");
            logger.debug("Remote commit hash: {}, Stored commit hash: {}", 
                remoteCommitHash, lastCommitHash);
            
            // Compare commit hashes
            boolean isUpdated = !remoteCommitHash.equals(lastCommitHash);
            
            if (isUpdated) {
                logger.info("Repository has been updated. Old: {}, New: {}", 
                    lastCommitHash, remoteCommitHash);
            } else {
                logger.debug("Repository is up to date");
            }
            
            return isUpdated;
            
        } catch (Exception e) {
            logger.error("Error checking repository updates for {}: {}", 
                repositoryUrl, e.getMessage());
            return false;
        }
    }
    
    @Override
    public void markWikiAsStale(String wikiId) {
        logger.info("Marking wiki as stale: {}", wikiId);
        
        try {
            Wiki wiki = wikiRepository.findById(wikiId)
                .orElseThrow(() -> new IllegalArgumentException("Wiki not found: " + wikiId));
            
            wiki.setStale(true);
            wikiRepository.save(wiki);
            
            logger.info("Wiki marked as stale successfully: {}", wikiId);
            
        } catch (Exception e) {
            logger.error("Failed to mark wiki as stale: {}", wikiId, e);
            throw e;
        }
    }
}
