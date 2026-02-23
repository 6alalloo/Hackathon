package com.codewiki.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HuggingFace Inference API client implementation.
 * Uses Spring WebFlux WebClient for async HTTP communication.
 * Implements retry logic with exponential backoff for resilience.
 */
@Component
public class HuggingFaceLLMClient implements LLMClient {
    
    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceLLMClient.class);
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final int retryAttempts;
    private final List<Long> retryDelays;

    @Autowired
    public HuggingFaceLLMClient(
            WebClient.Builder webClientBuilder,
            @Value("${huggingface.api.url}") String apiUrl,
            @Value("${huggingface.api.key}") String apiKey,
            @Value("${huggingface.api.model:Qwen/Qwen2.5-Coder-7B-Instruct}") String modelName,
            @Value("${huggingface.api.temperature:0.3}") double temperature,
            @Value("${huggingface.api.max-tokens:2048}") int maxTokens,
            @Value("${huggingface.api.retry-attempts:3}") int retryAttempts,
            @Value("${huggingface.api.retry-delays:1000,2000,4000}") String retryDelaysStr) {
        this(
                buildWebClient(webClientBuilder, apiUrl, apiKey, modelName),
                modelName,
                temperature,
                maxTokens,
                retryAttempts,
                retryDelaysStr
        );
    }

    // Convenience constructor for isolated tests.
    public HuggingFaceLLMClient(
            String apiUrl,
            String apiKey,
            String modelName,
            double temperature,
            int maxTokens,
            int retryAttempts,
            String retryDelaysStr) {
        this(
                buildWebClient(WebClient.builder(), apiUrl, apiKey, modelName),
                modelName,
                temperature,
                maxTokens,
                retryAttempts,
                retryDelaysStr
        );
    }

    private HuggingFaceLLMClient(
            WebClient webClient,
            String modelName,
            double temperature,
            int maxTokens,
            int retryAttempts,
            String retryDelaysStr) {
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.retryAttempts = retryAttempts;
        this.objectMapper = new ObjectMapper();
        
        // Parse retry delays from comma-separated string
        this.retryDelays = parseRetryDelays(retryDelaysStr);
        
        this.webClient = webClient;
        
        logger.info("HuggingFaceLLMClient initialized with model: {}", modelName);
    }

    private static WebClient buildWebClient(
            WebClient.Builder webClientBuilder,
            String apiUrl,
            String apiKey,
            String modelName) {
        return webClientBuilder
                .baseUrl(apiUrl + "/" + modelName)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    
    private List<Long> parseRetryDelays(String delaysStr) {
        try {
            return List.of(delaysStr.split(","))
                    .stream()
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        } catch (Exception e) {
            logger.warn("Failed to parse retry delays, using defaults: 1000, 2000, 4000", e);
            return List.of(1000L, 2000L, 4000L);
        }
    }
    
    @Override
    public String generateCompletion(String prompt, Map<String, Object> parameters) {
        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", prompt);
            
            // Add parameters (temperature, max_new_tokens, etc.)
            Map<String, Object> params = new HashMap<>();
            params.put("temperature", parameters.getOrDefault("temperature", temperature));
            params.put("max_new_tokens", parameters.getOrDefault("max_tokens", maxTokens));
            params.put("return_full_text", false);
            requestBody.put("parameters", params);
            
            logger.debug("Sending request to HuggingFace API for model: {}", modelName);
            
            // Make synchronous request with timeout
            String response = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            
            // Parse response
            return parseResponse(response);
            
        } catch (WebClientResponseException e) {
            logger.error("HuggingFace API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("LLM API request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during LLM API call", e);
            throw new RuntimeException("LLM API request failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String generateWithRetry(String prompt, int maxRetries) {
        int attempts = Math.min(maxRetries, retryAttempts);
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                logger.debug("LLM generation attempt {} of {}", attempt, attempts);
                
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("temperature", temperature);
                parameters.put("max_tokens", maxTokens);
                
                String result = generateCompletion(prompt, parameters);
                
                if (attempt > 1) {
                    logger.info("LLM generation succeeded on attempt {}", attempt);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < attempts) {
                    long delay = getRetryDelay(attempt - 1);
                    logger.warn("LLM generation attempt {} failed, retrying in {}ms: {}", 
                            attempt, delay, e.getMessage());
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry sleep interrupted", ie);
                        break;
                    }
                } else {
                    logger.error("LLM generation failed after {} attempts", attempts, e);
                }
            }
        }
        
        // All retries failed - return error message instead of throwing exception
        String errorMessage = "Failed to generate content after " + attempts + " attempts. " +
                "Error: " + (lastException != null ? lastException.getMessage() : "Unknown error");
        logger.error("Complete LLM failure: {}", errorMessage);
        
        return "Error: " + errorMessage;
    }
    
    private long getRetryDelay(int attemptIndex) {
        if (attemptIndex < retryDelays.size()) {
            return retryDelays.get(attemptIndex);
        }
        // Fallback to exponential backoff if not enough delays configured
        return (long) Math.pow(2, attemptIndex) * 1000;
    }
    
    private String parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            
            // HuggingFace API returns array of results
            if (root.isArray() && root.size() > 0) {
                JsonNode firstResult = root.get(0);
                if (firstResult.has("generated_text")) {
                    return firstResult.get("generated_text").asText();
                }
            }
            
            // Fallback: return raw response if parsing fails
            logger.warn("Unexpected response format, returning raw response");
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to parse LLM response", e);
            return response;
        }
    }
    
    @Override
    public boolean checkApiHealth() {
        try {
            // Simple health check - try to generate with minimal prompt
            Map<String, Object> params = new HashMap<>();
            params.put("temperature", 0.1);
            params.put("max_tokens", 10);
            
            generateCompletion("test", params);
            return true;
            
        } catch (Exception e) {
            logger.error("API health check failed", e);
            return false;
        }
    }
}
