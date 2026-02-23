package com.codewiki.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that adds contextual information to MDC for structured logging.
 * Includes request ID, wiki ID, and repository URL when available.
 */
@Slf4j
@Component
public class LoggingFilter implements Filter {
    
    private static final String REQUEST_ID = "requestId";
    private static final String WIKI_ID = "wikiId";
    private static final String REPOSITORY_URL = "repositoryUrl";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // Generate and set request ID
            String requestId = UUID.randomUUID().toString();
            MDC.put(REQUEST_ID, requestId);
            
            // Extract wiki ID from path if present (e.g., /api/wikis/{wikiId})
            String path = httpRequest.getRequestURI();
            if (path.contains("/wikis/")) {
                String[] parts = path.split("/wikis/");
                if (parts.length > 1) {
                    String wikiId = parts[1].split("/")[0];
                    MDC.put(WIKI_ID, wikiId);
                }
            }
            
            // Extract repository URL from query parameters if present
            String repoUrl = httpRequest.getParameter("repositoryUrl");
            if (repoUrl != null && !repoUrl.isEmpty()) {
                MDC.put(REPOSITORY_URL, sanitizeUrl(repoUrl));
            }
            
            log.debug("Incoming request: {} {}", httpRequest.getMethod(), path);
            
            chain.doFilter(request, response);
            
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.clear();
        }
    }
    
    /**
     * Sanitize URL to remove any sensitive information
     */
    private String sanitizeUrl(String url) {
        // Remove any query parameters or fragments that might contain sensitive data
        if (url.contains("?")) {
            url = url.substring(0, url.indexOf("?"));
        }
        if (url.contains("#")) {
            url = url.substring(0, url.indexOf("#"));
        }
        return url;
    }
}
