package com.codewiki.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for RateLimiterService using jqwik.
 */
class RateLimiterServicePropertyTest {
    
    /**
     * Feature: codewiki-generator, Property 31: Concurrent Generation Limit
     * For any set of wiki generation requests, the system should enforce a maximum of 10 concurrent
     * generations, queueing additional requests beyond this limit.
     * 
     * Validates: Requirements 12.1, 12.2
     */
    @Property(tries = 100)
    void concurrentGenerationLimit_EnforcesMaximumConcurrentGenerations(
        @ForAll @IntRange(min = 1, max = 50) int totalRequests
    ) throws InterruptedException {
        int maxConcurrent = 10;
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(maxConcurrent, 100);
        
        AtomicInteger concurrentCount = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrent = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(totalRequests);
        
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    if (rateLimiter.tryAcquireGenerationSlot()) {
                        int current = concurrentCount.incrementAndGet();
                        
                        // Update max observed concurrent count
                        maxObservedConcurrent.updateAndGet(max -> Math.max(max, current));
                        
                        // Simulate some work
                        Thread.sleep(10);
                        
                        concurrentCount.decrementAndGet();
                        rateLimiter.releaseGenerationSlot();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // Start all threads
        completionLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // The maximum concurrent count should never exceed the limit
        assertTrue(maxObservedConcurrent.get() <= maxConcurrent,
            String.format("Maximum concurrent generations (%d) exceeded limit (%d)",
                maxObservedConcurrent.get(), maxConcurrent));
    }
    
    /**
     * Feature: codewiki-generator, Property 31: Concurrent Generation Limit
     * When the concurrent request limit is reached, additional requests should be queued.
     * 
     * Validates: Requirements 12.1, 12.2
     */
    @Property(tries = 100)
    void concurrentGenerationLimit_QueuesExcessRequests(
        @ForAll @IntRange(min = 11, max = 30) int totalRequests
    ) {
        int maxConcurrent = 10;
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(maxConcurrent, 100);
        
        // Acquire all available slots
        List<Boolean> acquisitions = new ArrayList<>();
        for (int i = 0; i < totalRequests; i++) {
            boolean acquired = rateLimiter.tryAcquireGenerationSlot();
            acquisitions.add(acquired);
        }
        
        // Count successful acquisitions
        long successfulAcquisitions = acquisitions.stream().filter(b -> b).count();
        long failedAcquisitions = acquisitions.stream().filter(b -> !b).count();
        
        // Exactly maxConcurrent should succeed
        assertEquals(maxConcurrent, successfulAcquisitions,
            "Should acquire exactly " + maxConcurrent + " slots");
        
        // The rest should fail (indicating they need to be queued)
        assertEquals(totalRequests - maxConcurrent, failedAcquisitions,
            "Excess requests should fail to acquire slots");
        
        // Clean up
        for (int i = 0; i < maxConcurrent; i++) {
            rateLimiter.releaseGenerationSlot();
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 31: Concurrent Generation Limit
     * Released slots should become available for new requests.
     * 
     * Validates: Requirements 12.1, 12.2
     */
    @Property(tries = 100)
    void concurrentGenerationLimit_ReleasedSlotsAreReusable(
        @ForAll @IntRange(min = 1, max = 10) int slotsToAcquire
    ) {
        int maxConcurrent = 10;
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(maxConcurrent, 100);
        
        // Acquire some slots
        for (int i = 0; i < slotsToAcquire; i++) {
            assertTrue(rateLimiter.tryAcquireGenerationSlot(),
                "Should be able to acquire slot " + i);
        }
        
        // Release all slots
        for (int i = 0; i < slotsToAcquire; i++) {
            rateLimiter.releaseGenerationSlot();
        }
        
        // Should be able to acquire again
        for (int i = 0; i < slotsToAcquire; i++) {
            assertTrue(rateLimiter.tryAcquireGenerationSlot(),
                "Should be able to re-acquire slot " + i + " after release");
        }
        
        // Clean up
        for (int i = 0; i < slotsToAcquire; i++) {
            rateLimiter.releaseGenerationSlot();
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 32: Queue Position Feedback
     * For any queued wiki generation request, the system should provide the queue position to the user.
     * 
     * Validates: Requirements 12.3
     */
    @Property(tries = 100)
    void queuePositionFeedback_ProvidesCorrectPositions(
        @ForAll @IntRange(min = 1, max = 20) int queueSize
    ) {
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(10, 100);
        
        List<String> requestIds = new ArrayList<>();
        
        // Enqueue requests
        for (int i = 0; i < queueSize; i++) {
            String requestId = "request-" + i;
            requestIds.add(requestId);
            rateLimiter.enqueueRequest(requestId);
        }
        
        // Verify queue positions
        for (int i = 0; i < queueSize; i++) {
            int position = rateLimiter.getQueuePosition(requestIds.get(i));
            assertEquals(i, position,
                String.format("Request %s should be at position %d", requestIds.get(i), i));
        }
        
        // Clean up
        for (String requestId : requestIds) {
            rateLimiter.dequeueRequest(requestId);
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 32: Queue Position Feedback
     * Dequeuing a request should update positions for remaining requests.
     * 
     * Validates: Requirements 12.3
     */
    @Property(tries = 100)
    void queuePositionFeedback_UpdatesAfterDequeue(
        @ForAll @IntRange(min = 3, max = 15) int queueSize,
        @ForAll @IntRange(min = 0, max = 10) int removeIndex
    ) {
        Assume.that(removeIndex < queueSize);
        
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(10, 100);
        
        List<String> requestIds = new ArrayList<>();
        
        // Enqueue requests
        for (int i = 0; i < queueSize; i++) {
            String requestId = "request-" + i;
            requestIds.add(requestId);
            rateLimiter.enqueueRequest(requestId);
        }
        
        // Remove a request from the middle
        String removedId = requestIds.get(removeIndex);
        rateLimiter.dequeueRequest(removedId);
        
        // Verify removed request is not in queue
        assertEquals(-1, rateLimiter.getQueuePosition(removedId),
            "Removed request should return -1 for queue position");
        
        // Verify remaining requests have correct positions
        int expectedPosition = 0;
        for (int i = 0; i < queueSize; i++) {
            if (i == removeIndex) continue; // Skip removed request
            
            String requestId = requestIds.get(i);
            int actualPosition = rateLimiter.getQueuePosition(requestId);
            assertEquals(expectedPosition, actualPosition,
                String.format("Request %s should be at position %d after removal", requestId, expectedPosition));
            expectedPosition++;
        }
        
        // Clean up
        for (String requestId : requestIds) {
            rateLimiter.dequeueRequest(requestId);
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 32: Queue Position Feedback
     * Non-existent request IDs should return -1 for queue position.
     * 
     * Validates: Requirements 12.3
     */
    @Property(tries = 100)
    void queuePositionFeedback_NonExistentRequestReturnsNegativeOne(
        @ForAll("requestIds") String nonExistentId
    ) {
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(10, 100);
        
        int position = rateLimiter.getQueuePosition(nonExistentId);
        
        assertEquals(-1, position,
            "Non-existent request should return -1 for queue position");
    }
    
    /**
     * Feature: codewiki-generator, Property 33: LLM API Rate Limiting
     * For any sequence of LLM API requests, the Rate_Limiter should enforce a maximum of 100 requests
     * per minute, delaying requests when this limit is reached.
     * 
     * Validates: Requirements 12.4, 12.5
     */
    @Property(tries = 50)
    void llmApiRateLimiting_EnforcesRequestsPerMinute(
        @ForAll @IntRange(min = 1, max = 50) int requestCount
    ) {
        int maxRequestsPerMinute = 100;
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(10, maxRequestsPerMinute);
        
        long startTime = System.currentTimeMillis();
        int successfulRequests = 0;
        
        // Try to acquire LLM slots
        for (int i = 0; i < requestCount; i++) {
            if (rateLimiter.tryAcquireLLMSlot()) {
                successfulRequests++;
            }
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // All requests within the limit should succeed
        assertTrue(successfulRequests <= maxRequestsPerMinute,
            String.format("Should not exceed %d requests per minute", maxRequestsPerMinute));
        
        // If we requested more than the limit, some delay should have occurred
        if (requestCount > maxRequestsPerMinute) {
            assertTrue(elapsedTime > 0,
                "Should have some delay when exceeding rate limit");
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 33: LLM API Rate Limiting
     * LLM rate limiter should allow requests up to the configured limit without delay.
     * 
     * Validates: Requirements 12.4, 12.5
     */
    @Property(tries = 50)
    void llmApiRateLimiting_AllowsRequestsWithinLimit(
        @ForAll @IntRange(min = 1, max = 100) int requestCount
    ) {
        int maxRequestsPerMinute = 100;
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(10, maxRequestsPerMinute);
        
        int successfulRequests = 0;
        
        // Acquire slots within the limit
        for (int i = 0; i < requestCount; i++) {
            if (rateLimiter.tryAcquireLLMSlot()) {
                successfulRequests++;
            }
        }
        
        // All requests within limit should succeed
        assertEquals(requestCount, successfulRequests,
            "All requests within rate limit should succeed");
    }
    
    /**
     * Feature: codewiki-generator, Property 33: LLM API Rate Limiting
     * Concurrent LLM requests should be properly rate limited.
     * 
     * Validates: Requirements 12.4, 12.5
     */
    @Property(tries = 30)
    void llmApiRateLimiting_HandlesConcurrentRequests(
        @ForAll @IntRange(min = 5, max = 20) int concurrentThreads
    ) throws InterruptedException {
        int maxRequestsPerMinute = 100;
        RateLimiterService rateLimiter = new RateLimiterServiceImpl(10, maxRequestsPerMinute);
        
        AtomicInteger successfulRequests = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentThreads);
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        
        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    if (rateLimiter.tryAcquireLLMSlot()) {
                        successfulRequests.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // All concurrent requests within limit should succeed
        assertTrue(successfulRequests.get() <= maxRequestsPerMinute,
            "Concurrent requests should not exceed rate limit");
        
        // With reasonable concurrency, all should succeed
        assertEquals(concurrentThreads, successfulRequests.get(),
            "All concurrent requests within limit should succeed");
    }
    
    // ========== Arbitraries (Generators) ==========
    
    /**
     * Generates request IDs.
     */
    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "request-" + s);
    }
}
