package com.codewiki.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of rate limiting service using semaphores and token bucket algorithm.
 * Thread-safe implementation for controlling concurrent wiki generations and LLM API requests.
 */
@Service
public class RateLimiterServiceImpl implements RateLimiterService {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimiterServiceImpl.class);
    
    private final Semaphore generationSemaphore;
    private final LinkedBlockingQueue<String> requestQueue;
    private final ConcurrentHashMap<String, Integer> queuePositions;
    
    // Token bucket for LLM rate limiting
    private final int llmRequestsPerMinute;
    private final AtomicInteger availableTokens;
    private volatile Instant lastRefillTime;
    private final Object tokenLock = new Object();
    
    public RateLimiterServiceImpl(
            @Value("${rate-limit.concurrent-generations:10}") int concurrentGenerations,
            @Value("${rate-limit.llm-requests-per-minute:100}") int llmRequestsPerMinute) {
        
        this.generationSemaphore = new Semaphore(concurrentGenerations, true);
        this.requestQueue = new LinkedBlockingQueue<>();
        this.queuePositions = new ConcurrentHashMap<>();
        
        this.llmRequestsPerMinute = llmRequestsPerMinute;
        this.availableTokens = new AtomicInteger(llmRequestsPerMinute);
        this.lastRefillTime = Instant.now();
        
        log.info("RateLimiterService initialized with {} concurrent generations and {} LLM requests/minute",
                concurrentGenerations, llmRequestsPerMinute);
    }
    
    @Override
    public boolean tryAcquireGenerationSlot() {
        boolean acquired = generationSemaphore.tryAcquire();
        if (acquired) {
            log.debug("Generation slot acquired. Available permits: {}", generationSemaphore.availablePermits());
        } else {
            log.debug("Generation slot not available. Available permits: {}", generationSemaphore.availablePermits());
        }
        return acquired;
    }
    
    @Override
    public void releaseGenerationSlot() {
        generationSemaphore.release();
        log.debug("Generation slot released. Available permits: {}", generationSemaphore.availablePermits());
    }
    
    @Override
    public boolean tryAcquireLLMSlot() {
        synchronized (tokenLock) {
            refillTokensIfNeeded();
            
            if (availableTokens.get() > 0) {
                availableTokens.decrementAndGet();
                log.debug("LLM slot acquired. Available tokens: {}", availableTokens.get());
                return true;
            }
            
            // Calculate delay until next token is available
            long millisSinceRefill = Instant.now().toEpochMilli() - lastRefillTime.toEpochMilli();
            long millisPerToken = 60000 / llmRequestsPerMinute;
            long delayMillis = millisPerToken - (millisSinceRefill % millisPerToken);
            
            log.debug("LLM rate limit reached. Waiting {} ms for next token", delayMillis);
            
            try {
                Thread.sleep(delayMillis);
                refillTokensIfNeeded();
                
                if (availableTokens.get() > 0) {
                    availableTokens.decrementAndGet();
                    log.debug("LLM slot acquired after delay. Available tokens: {}", availableTokens.get());
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for LLM slot", e);
                return false;
            }
            
            return false;
        }
    }
    
    @Override
    public void releaseLLMSlot() {
        // Token bucket doesn't require explicit release
        // Tokens are automatically refilled based on time
        log.debug("LLM slot release called (no-op for token bucket)");
    }
    
    @Override
    public int getQueuePosition(String requestId) {
        Integer position = queuePositions.get(requestId);
        return position != null ? position : -1;
    }
    
    @Override
    public void enqueueRequest(String requestId) {
        requestQueue.offer(requestId);
        updateQueuePositions();
        log.debug("Request {} enqueued. Queue size: {}", requestId, requestQueue.size());
    }
    
    @Override
    public void dequeueRequest(String requestId) {
        requestQueue.remove(requestId);
        queuePositions.remove(requestId);
        updateQueuePositions();
        log.debug("Request {} dequeued. Queue size: {}", requestId, requestQueue.size());
    }
    
    /**
     * Refills tokens based on elapsed time since last refill.
     * Called within synchronized block.
     */
    private void refillTokensIfNeeded() {
        Instant now = Instant.now();
        long millisSinceRefill = now.toEpochMilli() - lastRefillTime.toEpochMilli();
        
        if (millisSinceRefill >= 60000) {
            // Full minute has passed, refill to maximum
            availableTokens.set(llmRequestsPerMinute);
            lastRefillTime = now;
            log.debug("Token bucket refilled to {} tokens", llmRequestsPerMinute);
        }
    }
    
    /**
     * Updates queue positions for all requests in the queue.
     */
    private void updateQueuePositions() {
        queuePositions.clear();
        int position = 0;
        for (String requestId : requestQueue) {
            queuePositions.put(requestId, position++);
        }
    }
}
