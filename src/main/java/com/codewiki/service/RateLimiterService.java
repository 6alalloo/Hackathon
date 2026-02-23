package com.codewiki.service;

/**
 * Service for managing rate limiting across wiki generation and LLM API requests.
 * Provides thread-safe mechanisms for controlling concurrent operations and request rates.
 */
public interface RateLimiterService {
    
    /**
     * Attempts to acquire a slot for wiki generation.
     * 
     * @return true if a slot was acquired, false if the limit is reached
     */
    boolean tryAcquireGenerationSlot();
    
    /**
     * Releases a wiki generation slot, making it available for other requests.
     */
    void releaseGenerationSlot();
    
    /**
     * Attempts to acquire a slot for an LLM API request.
     * This method may block if the rate limit is reached.
     * 
     * @return true if a slot was acquired, false if interrupted while waiting
     */
    boolean tryAcquireLLMSlot();
    
    /**
     * Releases an LLM API request slot.
     */
    void releaseLLMSlot();
    
    /**
     * Gets the current queue position for a request.
     * 
     * @param requestId the unique identifier for the request
     * @return the queue position (0-based), or -1 if not in queue
     */
    int getQueuePosition(String requestId);
    
    /**
     * Adds a request to the queue.
     * 
     * @param requestId the unique identifier for the request
     */
    void enqueueRequest(String requestId);
    
    /**
     * Removes a request from the queue.
     * 
     * @param requestId the unique identifier for the request
     */
    void dequeueRequest(String requestId);
}
