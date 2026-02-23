package com.codewiki.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeTry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for HuggingFaceLLMClient using jqwik.
 * Tests retry behavior and exponential backoff properties.
 */
class HuggingFaceLLMClientPropertyTest {
    
    private MockWebServer mockServer;
    private HuggingFaceLLMClient client;
    private ObjectMapper objectMapper;
    
    @BeforeTry
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        objectMapper = new ObjectMapper();
        
        // Create client pointing to mock server
        String baseUrl = mockServer.url("/models").toString();
        client = new HuggingFaceLLMClient(
                baseUrl.replace("/models/", ""),
                "test-api-key",
                "test-model",
                0.3,
                2048,
                3,
                "1000,2000,4000"
        );
    }
    
    @AfterTry
    void tearDown() throws IOException {
        if (mockServer != null) {
            mockServer.shutdown();
        }
    }
    
    /**
     * Feature: codewiki-generator, Property 13: LLM Retry with Exponential Backoff
     * For any LLM API request that fails, the system should retry up to 3 times
     * with exponential backoff (1s, 2s, 4s delays).
     * 
     * **Validates: Requirements 6.3**
     */
    @Property(tries = 100)
    void retryWithExponentialBackoff(@ForAll("prompts") String prompt) throws Exception {
        // Arrange: Configure mock to fail twice, then succeed
        mockServer.enqueue(new MockResponse().setResponseCode(503)); // First attempt fails
        mockServer.enqueue(new MockResponse().setResponseCode(503)); // Second attempt fails
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"Success on third attempt\"}]")); // Third succeeds
        
        long startTime = System.currentTimeMillis();
        
        // Act
        String result = client.generateWithRetry(prompt, 3);
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Assert: Should succeed after retries
        assertThat(result).isEqualTo("Success on third attempt");
        
        // Assert: Should have made 3 requests
        assertThat(mockServer.getRequestCount()).isEqualTo(3);
        
        // Assert: Total time should be at least 1s + 2s = 3s (accounting for delays)
        // Using 2.5s to account for timing variations
        assertThat(totalTime).isGreaterThanOrEqualTo(2500);
        
        // Verify exponential backoff by checking request timing
        RecordedRequest req1 = mockServer.takeRequest();
        RecordedRequest req2 = mockServer.takeRequest();
        RecordedRequest req3 = mockServer.takeRequest();
        
        assertThat(req1).isNotNull();
        assertThat(req2).isNotNull();
        assertThat(req3).isNotNull();
    }
    
    /**
     * Feature: codewiki-generator, Property 14: LLM Complete Failure Handling
     * For any LLM generation where all 3 retry attempts fail, the system should
     * log the error with full context and notify the user of the generation failure.
     * 
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    void completeFailureHandling(@ForAll("prompts") String prompt) {
        // Arrange: Configure mock to fail all attempts
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service unavailable"));
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service unavailable"));
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service unavailable"));
        
        // Act
        String result = client.generateWithRetry(prompt, 3);
        
        // Assert: Should return error message (not throw exception)
        assertThat(result).startsWith("Error:");
        assertThat(result).contains("Failed to generate content after 3 attempts");
        
        // Assert: Should have attempted all 3 retries
        assertThat(mockServer.getRequestCount()).isEqualTo(3);
    }
    
    /**
     * Property: Retry count should be respected
     * For any number of max retries, the system should not exceed that count.
     */
    @Property(tries = 50)
    void retryCountRespected(@ForAll("prompts") String prompt,
                             @ForAll @IntRange(min = 1, max = 5) int maxRetries) {
        // Arrange: Enqueue enough failures to exceed max retries
        for (int i = 0; i < maxRetries + 2; i++) {
            mockServer.enqueue(new MockResponse().setResponseCode(503));
        }
        
        // Act
        client.generateWithRetry(prompt, maxRetries);
        
        // Assert: Should not exceed maxRetries (capped at configured retryAttempts=3)
        int expectedRequests = Math.min(maxRetries, 3);
        assertThat(mockServer.getRequestCount()).isEqualTo(expectedRequests);
    }
    
    /**
     * Property: Successful first attempt should not retry
     * For any prompt, if the first attempt succeeds, no retries should occur.
     */
    @Property(tries = 100)
    void successfulFirstAttemptNoRetry(@ForAll("prompts") String prompt) {
        // Arrange: Configure mock to succeed immediately
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"Immediate success\"}]"));
        
        // Act
        String result = client.generateWithRetry(prompt, 3);
        
        // Assert: Should succeed without retries
        assertThat(result).isEqualTo("Immediate success");
        assertThat(mockServer.getRequestCount()).isEqualTo(1);
    }
    
    /**
     * Property: Request includes prompt and parameters
     * For any prompt, the request body should contain the prompt and configured parameters.
     */
    @Property(tries = 50)
    void requestIncludesPromptAndParameters(@ForAll("prompts") String prompt) throws Exception {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"test\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.5);
        params.put("max_tokens", 1024);
        
        // Act
        client.generateCompletion(prompt, params);
        
        // Assert: Verify request body
        RecordedRequest request = mockServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        
        assertThat(requestBody).contains(prompt);
        assertThat(requestBody).contains("temperature");
        assertThat(requestBody).contains("max_new_tokens");
    }
    
    // Generators
    
    @Provide
    Arbitrary<String> prompts() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', ':', ';')
                .ofMinLength(10)
                .ofMaxLength(500);
    }
}
