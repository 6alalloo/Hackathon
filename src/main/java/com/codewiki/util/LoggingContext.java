package com.codewiki.util;

import org.slf4j.MDC;

/**
 * Utility class for managing logging context (MDC).
 * Provides methods to add contextual information to logs.
 */
public class LoggingContext {
    
    private static final String WIKI_ID = "wikiId";
    private static final String REPOSITORY_URL = "repositoryUrl";
    private static final String REQUEST_ID = "requestId";
    
    private LoggingContext() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Set wiki ID in logging context
     */
    public static void setWikiId(String wikiId) {
        if (wikiId != null && !wikiId.isEmpty()) {
            MDC.put(WIKI_ID, wikiId);
        }
    }
    
    /**
     * Set repository URL in logging context (sanitized)
     */
    public static void setRepositoryUrl(String repositoryUrl) {
        if (repositoryUrl != null && !repositoryUrl.isEmpty()) {
            MDC.put(REPOSITORY_URL, sanitizeUrl(repositoryUrl));
        }
    }
    
    /**
     * Set request ID in logging context
     */
    public static void setRequestId(String requestId) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(REQUEST_ID, requestId);
        }
    }
    
    /**
     * Clear wiki ID from logging context
     */
    public static void clearWikiId() {
        MDC.remove(WIKI_ID);
    }
    
    /**
     * Clear repository URL from logging context
     */
    public static void clearRepositoryUrl() {
        MDC.remove(REPOSITORY_URL);
    }
    
    /**
     * Clear all logging context
     */
    public static void clearAll() {
        MDC.clear();
    }
    
    /**
     * Sanitize URL to remove sensitive information
     */
    private static String sanitizeUrl(String url) {
        if (url == null) {
            return null;
        }
        
        // Remove query parameters and fragments
        String sanitized = url;
        if (sanitized.contains("?")) {
            sanitized = sanitized.substring(0, sanitized.indexOf("?"));
        }
        if (sanitized.contains("#")) {
            sanitized = sanitized.substring(0, sanitized.indexOf("#"));
        }
        
        // Remove any tokens or API keys from URL
        sanitized = sanitized.replaceAll("(token|key|password)=[^&]*", "$1=***");
        
        return sanitized;
    }
    
    /**
     * Sanitize API key for logging (show only first/last 4 characters)
     */
    public static String sanitizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
