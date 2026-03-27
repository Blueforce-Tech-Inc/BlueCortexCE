package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Profile("!prod")
@Tag(name = "Test", description = "AI model connectivity test endpoints. Only available in non-production environments (@Profile !prod).")
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    private final LlmService llmService;
    private final EmbeddingService embeddingService;

    public TestController(LlmService llmService, EmbeddingService embeddingService) {
        this.llmService = llmService;
        this.embeddingService = embeddingService;
    }

    /**
     * Test LLM (DeepSeek) connectivity.
     */
    @GetMapping("/llm")
    @Operation(summary = "Test LLM (DeepSeek) connectivity",
        description = "Tests connectivity to the configured LLM provider (DeepSeek). Sends a simple prompt and returns the model's response. Use to verify API key configuration and connectivity.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "LLM test completed (status may be 'success', 'error', or 'disabled')"),
        @ApiResponse(responseCode = "500", description = "LLM test failed with internal error")
    })
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
    @Operation(summary = "Test Embedding (SiliconFlow BGE-M3) connectivity",
        description = "Tests connectivity to the configured embedding provider (SiliconFlow BGE-M3). Generates an embedding for a test string and returns the dimensionality. Returns 'disabled' status if no API key is configured.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Embedding test completed (status may be 'success', 'error', or 'disabled')"),
        @ApiResponse(responseCode = "500", description = "Embedding test failed with internal error")
    })
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
    @Operation(summary = "Test both LLM and Embedding",
        description = "Runs both LLM and Embedding connectivity tests and returns combined results in a single response.")
    @ApiResponse(responseCode = "200", description = "All tests completed")
    public ResponseEntity<Map<String, Object>> testAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("llm", testLlm().getBody());
        result.put("embedding", testEmbedding().getBody());
        return ResponseEntity.ok(result);
    }
}
