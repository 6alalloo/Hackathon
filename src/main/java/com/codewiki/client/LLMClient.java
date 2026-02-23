package com.codewiki.client;

import java.util.Map;

/**
 * Client interface for LLM API operations.
 * Handles communication with HuggingFace Inference API for code documentation generation.
 */
public interface LLMClient {
    
    /**
     * Generates a completion from the LLM given a prompt and parameters.
     * 
     * @param prompt the input prompt for the LLM
     * @param parameters additional parameters (temperature, max_tokens, etc.)
     * @return the generated completion text
     */
    String generateCompletion(String prompt, Map<String, Object> parameters);
    
    /**
     * Generates a completion with automatic retry logic.
     * Retries on failure with exponential backoff.
     * 
     * @param prompt the input prompt for the LLM
     * @param maxRetries maximum number of retry attempts
     * @return the generated completion text, or error message on complete failure
     */
    String generateWithRetry(String prompt, int maxRetries);
    
    /**
     * Checks if the LLM API is healthy and accessible.
     * 
     * @return true if the API is accessible, false otherwise
     */
    boolean checkApiHealth();
}
