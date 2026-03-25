package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test controller for validating AI model configurations.
 * P1: Only enabled in non-production environments to prevent exposure of sensitive endpoints.
 */
@RestController
@RequestMapping("/api/test")
@Profile("!prod")  // P1: Disable in production to prevent exposure
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    @Autowired
    private LlmService llmService;

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * Test LLM (DeepSeek) connectivity.
     */
    @GetMapping("/llm")
    public ResponseEntity<Map<String, Object>> testLlm() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "testing");
        result.put("message", "Testing LLM (DeepSeek)...");

        try {
            String response = llmService.chatCompletion("You are a helpful assistant.", "Say 'Hello from DeepSeek!' in exactly these words.");
            result.put("status", "success");
            result.put("message", "LLM (DeepSeek) is working!");
            result.put("response", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("LLM test failed", e);
            result.put("status", "error");
            result.put("message", "LLM (DeepSeek) failed: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Test Embedding (SiliconFlow BGE-M3) connectivity.
     */
    @GetMapping("/embedding")
    public ResponseEntity<Map<String, Object>> testEmbedding() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "testing");
        result.put("message", "Testing Embedding (SiliconFlow BGE-M3)...");

        try {
            // Check if embedding is configured
            if (!embeddingService.isAvailable()) {
                result.put("status", "disabled");
                result.put("message", "Embedding is not configured (no API key)");
                result.put("hint", "Set spring.ai.openai.embedding.api-key in application-dev.yml");
                return ResponseEntity.ok(result);
            }

            float[] embedding = embeddingService.embed("Test document for embedding");
            result.put("status", "success");
            result.put("message", "Embedding (SiliconFlow BGE-M3) is working!");
            result.put("dimensions", embedding.length);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Embedding test failed", e);
            result.put("status", "error");
            result.put("message", "Embedding failed: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Test both LLM and Embedding.
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> testAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("llm", testLlm().getBody());
        result.put("embedding", testEmbedding().getBody());
        return ResponseEntity.ok(result);
    }
}
