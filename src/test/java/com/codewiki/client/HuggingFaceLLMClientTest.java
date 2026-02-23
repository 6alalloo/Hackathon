package com.codewiki.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for HuggingFaceLLMClient.
 * Tests specific examples and edge cases with mocked HuggingFace API.
 */
class HuggingFaceLLMClientTest {
    
    private MockWebServer mockServer;
    private HuggingFaceLLMClient client;
    
    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        
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
    
    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }
    
    @Test
    void testSuccessfulApiCall() {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"This is a test response\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.3);
        params.put("max_tokens", 2048);
        
        // Act
        String result = client.generateCompletion("Test prompt", params);
        
        // Assert
        assertThat(result).isEqualTo("This is a test response");
    }
    
    @Test
    void testSingleRetryScenario() throws Exception {
        // Arrange: Fail once, then succeed
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"Success after retry\"}]"));
        
        long startTime = System.currentTimeMillis();
        
        // Act
        String result = client.generateWithRetry("Test prompt", 3);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Assert
        assertThat(result).isEqualTo("Success after retry");
        assertThat(mockServer.getRequestCount()).isEqualTo(2);
        assertThat(duration).isGreaterThanOrEqualTo(900); // At least 1s delay
    }
    
    @Test
    void testCompleteFailureAfterThreeRetries() {
        // Arrange: All attempts fail
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service unavailable"));
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service unavailable"));
        mockServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service unavailable"));
        
        // Act
        String result = client.generateWithRetry("Test prompt", 3);
        
        // Assert: Should return error message, not throw exception
        assertThat(result).startsWith("Error:");
        assertThat(result).contains("Failed to generate content after 3 attempts");
        assertThat(mockServer.getRequestCount()).isEqualTo(3);
    }
    
    @Test
    void testRequestIncludesAuthorizationHeader() throws Exception {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"test\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        
        // Act
        client.generateCompletion("Test prompt", params);
        
        // Assert
        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
    }
    
    @Test
    void testRequestIncludesPromptInBody() throws Exception {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"test\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        String testPrompt = "Generate documentation for this code";
        
        // Act
        client.generateCompletion(testPrompt, params);
        
        // Assert
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains(testPrompt);
        assertThat(body).contains("inputs");
    }
    
    @Test
    void testRequestIncludesParameters() throws Exception {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"test\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", 0.5);
        params.put("max_tokens", 1024);
        
        // Act
        client.generateCompletion("Test prompt", params);
        
        // Assert
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("temperature");
        assertThat(body).contains("0.5");
        assertThat(body).contains("max_new_tokens");
        assertThat(body).contains("1024");
    }
    
    @Test
    void testApiErrorHandling() {
        // Arrange: Return error response
        mockServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\": \"Invalid request\"}"));
        
        Map<String, Object> params = new HashMap<>();
        
        // Act & Assert
        assertThatThrownBy(() -> client.generateCompletion("Test prompt", params))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM API request failed");
    }
    
    @Test
    void testUnexpectedResponseFormat() {
        // Arrange: Return unexpected format
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"unexpected\": \"format\"}"));
        
        Map<String, Object> params = new HashMap<>();
        
        // Act
        String result = client.generateCompletion("Test prompt", params);
        
        // Assert: Should return raw response as fallback
        assertThat(result).contains("unexpected");
    }
    
    @Test
    void testEmptyPrompt() {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"Empty response\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        
        // Act
        String result = client.generateCompletion("", params);
        
        // Assert
        assertThat(result).isEqualTo("Empty response");
    }
    
    @Test
    void testLongPrompt() {
        // Arrange
        String longPrompt = "a".repeat(5000);
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"Long prompt response\"}]"));
        
        Map<String, Object> params = new HashMap<>();
        
        // Act
        String result = client.generateCompletion(longPrompt, params);
        
        // Assert
        assertThat(result).isEqualTo("Long prompt response");
    }
    
    @Test
    void testHealthCheckSuccess() {
        // Arrange
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"generated_text\": \"test\"}]"));
        
        // Act
        boolean healthy = client.checkApiHealth();
        
        // Assert
        assertThat(healthy).isTrue();
    }
    
    @Test
    void testHealthCheckFailure() {
        // Arrange
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        
        // Act
        boolean healthy = client.checkApiHealth();
        
        // Assert
        assertThat(healthy).isFalse();
    }
}
