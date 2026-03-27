package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.List;

/**
 * Demo controller for Extraction APIs (Phase 3).
 * Demonstrates structured extraction query and trigger endpoints.
 */
@RestController
@RequestMapping("/demo/extraction")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final CortexMemClient client;

    public ExtractionController(CortexMemClient client) {
        this.client = client;
    }

    /**
     * GET /demo/extraction/latest?project=/test&template=user_preferences&userId=alice
     */
    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getLatest(
            @RequestParam String project,
            @RequestParam String template,
            @RequestParam(required = false) String userId) {

        if (project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }
        if (template.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "template is required"));
        }

        try {
            Map<String, Object> result = client.getLatestExtraction(project, template, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Get latest extraction failed for project={}, template={}", project, template, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Get latest extraction failed: " + e.getMessage()));
        }
    }

    /**
     * GET /demo/extraction/history?project=/test&template=user_preferences&userId=alice&limit=10
     */
    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getHistory(
            @RequestParam String project,
            @RequestParam String template,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") Integer limit) {

        if (project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }
        if (template.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "template is required"));
        }
        if (limit < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be non-negative"));
        }

        try {
            List<Map<String, Object>> result = client.getExtractionHistory(project, template, userId, limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Get extraction history failed for project={}, template={}", project, template, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Get extraction history failed: " + e.getMessage()));
        }
    }

    /**
     * POST /demo/extraction/run?projectPath=/test
     */
    @PostMapping(value = "/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> triggerExtraction(
            @RequestParam String projectPath) {

        if (projectPath.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "projectPath is required"));
        }

        try {
            client.triggerExtraction(projectPath);
            return ResponseEntity.ok(Map.of("status", "extraction triggered", "projectPath", projectPath));
        } catch (Exception e) {
            log.error("Trigger extraction failed for projectPath={}", projectPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Trigger extraction failed: " + e.getMessage()));
        }
    }

    /**
     * GET /demo/extraction/health
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "extraction", "status", "ok"));
    }
}
