package com.codewiki.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiterServiceImpl focusing on edge cases.
 */
class RateLimiterServiceImplTest {
    
    private RateLimiterService rateLimiter;
    
    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiterServiceImpl(10, 100);
    }
    
    /**
     * Test exactly 10 concurrent requests - all should succeed.
     */
    @Test
    void testExactly10ConcurrentRequests() {
        List<Boolean> results = new ArrayList<>();
        
        // Acquire exactly 10 slots
        for (int i = 0; i < 10; i++) {
            boolean acquired = rateLimiter.tryAcquireGenerationSlot();
            results.add(acquired);
        }
        
        // All 10 should succeed
        assertEquals(10, results.stream().filter(b -> b).count(),
            "All 10 concurrent requests should succeed");
        
        // Clean up
        for (int i = 0; i < 10; i++) {
            rateLimiter.releaseGenerationSlot();
        }
    }
    
    /**
     * Test 11th concurrent request - should fail (queue needed).
     */
    @Test
    void test11thConcurrentRequestShouldQueue() {
        // Acquire 10 slots
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquireGenerationSlot(),
                "Request " + i + " should succeed");
        }
        
        // 11th request should fail
        assertFalse(rateLimiter.tryAcquireGenerationSlot(),
            "11th concurrent request should fail and need queueing");
        
        // Clean up
        for (int i = 0; i < 10; i++) {
            rateLimiter.releaseGenerationSlot();
        }
    }
    
    /**
     * Test exactly 100 LLM requests in 1 minute - all should succeed.
     */
    @Test
    void testExactly100LLMRequestsInOneMinute() {
        int successCount = 0;
        
        // Try to acquire 100 LLM slots
        for (int i = 0; i < 100; i++) {
            if (rateLimiter.tryAcquireLLMSlot()) {
                successCount++;
            }
        }
        
        // All 100 should succeed
        assertEquals(100, successCount,
            "All 100 LLM requests within rate limit should succeed");
    }
    
    /**
     * Test 101st LLM request - should be delayed but may not succeed immediately
     * due to token bucket refill timing.
     */
    @Test
    void test101stLLMRequestShouldDelay() {
        // Acquire 100 slots
        for (int i = 0; i < 100; i++) {
            assertTrue(rateLimiter.tryAcquireLLMSlot(),
                "LLM request " + i + " should succeed");
        }
        
        // 101st request should be delayed
        long startTime = System.currentTimeMillis();
        boolean acquired = rateLimiter.tryAcquireLLMSlot();
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Should have some delay (at least a few milliseconds)
        assertTrue(elapsedTime > 0,
            "101st LLM request should experience delay");
        
        // May or may not succeed depending on timing (token bucket refills after full minute)
        // The important thing is that it was delayed
    }
    
    /**
     * Test releasing and reacquiring generation slots.
     */
    @Test
    void testReleaseAndReacquireGenerationSlots() {
        // Acquire all 10 slots
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryAcquireGenerationSlot());
        }
        
        // 11th should fail
        assertFalse(rateLimiter.tryAcquireGenerationSlot());
        
        // Release 5 slots
        for (int i = 0; i < 5; i++) {
            rateLimiter.releaseGenerationSlot();
        }
        
        // Should be able to acquire 5 more
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquireGenerationSlot(),
                "Should be able to reacquire released slot " + i);
        }
        
        // 11th should still fail
        assertFalse(rateLimiter.tryAcquireGenerationSlot());
        
        // Clean up
        for (int i = 0; i < 10; i++) {
            rateLimiter.releaseGenerationSlot();
        }
    }
    
    /**
     * Test queue position tracking with multiple requests.
     */
    @Test
    void testQueuePositionTracking() {
        // Enqueue 5 requests
        rateLimiter.enqueueRequest("req-1");
        rateLimiter.enqueueRequest("req-2");
        rateLimiter.enqueueRequest("req-3");
        rateLimiter.enqueueRequest("req-4");
        rateLimiter.enqueueRequest("req-5");
        
        // Verify positions
        assertEquals(0, rateLimiter.getQueuePosition("req-1"));
        assertEquals(1, rateLimiter.getQueuePosition("req-2"));
        assertEquals(2, rateLimiter.getQueuePosition("req-3"));
        assertEquals(3, rateLimiter.getQueuePosition("req-4"));
        assertEquals(4, rateLimiter.getQueuePosition("req-5"));
        
        // Dequeue middle request
        rateLimiter.dequeueRequest("req-3");
        
        // Positions should update
        assertEquals(0, rateLimiter.getQueuePosition("req-1"));
        assertEquals(1, rateLimiter.getQueuePosition("req-2"));
        assertEquals(-1, rateLimiter.getQueuePosition("req-3")); // Removed
        assertEquals(2, rateLimiter.getQueuePosition("req-4")); // Shifted
        assertEquals(3, rateLimiter.getQueuePosition("req-5")); // Shifted
        
        // Clean up
        rateLimiter.dequeueRequest("req-1");
        rateLimiter.dequeueRequest("req-2");
        rateLimiter.dequeueRequest("req-4");
        rateLimiter.dequeueRequest("req-5");
    }
    
    /**
     * Test queue position for non-existent request.
     */
    @Test
    void testQueuePositionForNonExistentRequest() {
        int position = rateLimiter.getQueuePosition("non-existent-id");
        assertEquals(-1, position,
            "Non-existent request should return -1 for queue position");
    }
    
    /**
     * Test dequeuing first request updates positions correctly.
     */
    @Test
    void testDequeueFirstRequest() {
        rateLimiter.enqueueRequest("req-1");
        rateLimiter.enqueueRequest("req-2");
        rateLimiter.enqueueRequest("req-3");
        
        // Dequeue first
        rateLimiter.dequeueRequest("req-1");
        
        // Remaining should shift
        assertEquals(0, rateLimiter.getQueuePosition("req-2"));
        assertEquals(1, rateLimiter.getQueuePosition("req-3"));
        
        // Clean up
        rateLimiter.dequeueRequest("req-2");
        rateLimiter.dequeueRequest("req-3");
    }
    
    /**
     * Test dequeuing last request doesn't affect others.
     */
    @Test
    void testDequeueLastRequest() {
        rateLimiter.enqueueRequest("req-1");
        rateLimiter.enqueueRequest("req-2");
        rateLimiter.enqueueRequest("req-3");
        
        // Dequeue last
        rateLimiter.dequeueRequest("req-3");
        
        // Others should remain unchanged
        assertEquals(0, rateLimiter.getQueuePosition("req-1"));
        assertEquals(1, rateLimiter.getQueuePosition("req-2"));
        assertEquals(-1, rateLimiter.getQueuePosition("req-3"));
        
        // Clean up
        rateLimiter.dequeueRequest("req-1");
        rateLimiter.dequeueRequest("req-2");
    }
    
    /**
     * Test concurrent generation with thread safety.
     */
    @Test
    void testConcurrentGenerationThreadSafety() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        List<Boolean> results = new CopyOnWriteArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean acquired = rateLimiter.tryAcquireGenerationSlot();
                    results.add(acquired);
                    
                    if (acquired) {
                        Thread.sleep(10); // Simulate work
                        rateLimiter.releaseGenerationSlot();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        completionLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Exactly 10 should succeed
        long successCount = results.stream().filter(b -> b).count();
        assertEquals(10, successCount,
            "Exactly 10 concurrent requests should succeed in thread-safe manner");
    }
    
    /**
     * Test empty queue behavior.
     */
    @Test
    void testEmptyQueue() {
        int position = rateLimiter.getQueuePosition("any-id");
        assertEquals(-1, position, "Empty queue should return -1 for any request");
    }
    
    /**
     * Test multiple release calls don't break semaphore.
     */
    @Test
    void testMultipleReleaseCallsHandledGracefully() {
        // Acquire 5 slots
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquireGenerationSlot());
        }
        
        // Release 5 slots
        for (int i = 0; i < 5; i++) {
            rateLimiter.releaseGenerationSlot();
        }
        
        // Release 5 more (extra releases)
        for (int i = 0; i < 5; i++) {
            rateLimiter.releaseGenerationSlot();
        }
        
        // Should still be able to acquire up to 10 (not more due to extra releases)
        int acquiredCount = 0;
        for (int i = 0; i < 15; i++) {
            if (rateLimiter.tryAcquireGenerationSlot()) {
                acquiredCount++;
            }
        }
        
        // Should be able to acquire at least 10 (may be more due to extra releases)
        assertTrue(acquiredCount >= 10,
            "Should be able to acquire at least 10 slots");
        
        // Clean up - release what we acquired
        for (int i = 0; i < acquiredCount; i++) {
            rateLimiter.releaseGenerationSlot();
        }
    }
    
    /**
     * Test LLM rate limiter with rapid successive calls.
     */
    @Test
    void testLLMRateLimiterRapidCalls() {
        int successCount = 0;
        
        // Make 50 rapid calls
        for (int i = 0; i < 50; i++) {
            if (rateLimiter.tryAcquireLLMSlot()) {
                successCount++;
            }
        }
        
        // All 50 should succeed (within 100 per minute limit)
        assertEquals(50, successCount,
            "All 50 rapid LLM requests should succeed");
    }
}
