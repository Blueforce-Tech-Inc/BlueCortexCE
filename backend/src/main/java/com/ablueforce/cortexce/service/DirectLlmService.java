package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.HashMap;

/**
 * Direct LLM service using REST API.
 * 
 * Bypasses Spring AI autoconfiguration issues by making direct HTTP calls.
 */
@Service
public class DirectLlmService {

    private static final Logger log = LoggerFactory.getLogger(DirectLlmService.class);

    private String apiKey;
    private String baseUrl;
    private String model;

    private final RestTemplate restTemplate;

    @Autowired
    public DirectLlmService(Environment env) {
        this.restTemplate = new RestTemplate();
        
        // Load configuration from environment - try multiple sources
        this.apiKey = System.getenv("SPRING_AI_OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            this.apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            this.apiKey = env.getProperty("spring.ai.openai.api-key", "");
        }
        
        this.baseUrl = System.getenv("SPRING_AI_OPENAI_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            this.baseUrl = System.getenv("OPENAI_BASE_URL");
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            this.baseUrl = env.getProperty("spring.ai.openai.base-url", "https://api.deepseek.com");
        }
        
        this.model = env.getProperty("spring.ai.openai.chat.options.model", "deepseek-chat");
        
        log.info("DirectLlmService initialized: apiKey={}, baseUrl={}, model={}", 
            apiKey != null ? "set (" + apiKey.length() + " chars)" : "null", baseUrl, model);
    }

    /**
     * Check if LLM is available.
     */
    public boolean isAvailable() {
        boolean available = apiKey != null && !apiKey.isEmpty();
        log.info("LLM availability check: apiKey={}, available={}", 
            apiKey != null ? "set (" + apiKey.length() + " chars)" : "null", available);
        return available;
    }

    /**
     * Simple chat completion.
     */
    public String chat(String userMessage) {
        return chat("You are a helpful assistant.", userMessage);
    }

    /**
     * Chat completion with system prompt.
     */
    public String chat(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            log.warn("LLM not available - no API key configured");
            return null;
        }

        try {
            // Build request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            });
            requestBody.put("temperature", 0.7);

            // Make request
            String url = baseUrl + "/v1/chat/completions";
            final String authKey = apiKey;
            restTemplate.setInterceptors(java.util.List.of(
                (req, body, execution) -> {
                    req.getHeaders().add("Authorization", "Bearer " + authKey);
                    req.getHeaders().add("Content-Type", "application/json");
                    return execution.execute(req, body);
                }
            ));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
            
            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> choices = (java.util.List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    return (String) message.get("content");
                }
            }
            
            log.warn("Unexpected response format from LLM: {}", response);
            return null;

        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Quality analysis using LLM.
     */
    public String analyzeQuality(String title, String type, String content, String facts) {
        String prompt = String.format("""
            Analyze this observation and provide a quality score.
            
            Title: %s
            Type: %s
            Content: %s
            Facts: %s
            
            Respond in JSON format:
            {"quality_score": 0.0-1.0, "feedback_type": "SUCCESS|PARTIAL|FAILURE", "reasoning": "..."}
            """, title, type, content, facts);

        return chat("You are a software quality analyst.", prompt);
    }
}
