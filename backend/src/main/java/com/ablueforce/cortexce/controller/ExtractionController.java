package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.service.StructuredExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Extraction query API controller (Phase 3).
 *
 * Provides endpoints for:
 * - Querying latest extraction results by template name
 * - Querying extraction history
 * - Manually triggering extraction
 */
@RestController
@RequestMapping("/api/extraction")
public class ExtractionController {

    private static final Logger log = LoggerFactory.getLogger(ExtractionController.class);

    private final StructuredExtractionService extractionService;

    public ExtractionController(StructuredExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    /**
     * Get latest extraction result for a template.
     * GET /api/extraction/{templateName}/latest?projectPath=/path&userId=alice
     */
    @GetMapping("/{templateName}/latest")
    public ResponseEntity<Map<String, Object>> getLatestExtraction(
            @PathVariable String templateName,
            @RequestParam String projectPath,
            @RequestParam(required = false) String userId) {

        if (templateName == null || templateName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "templateName is required"
            ));
        }
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "projectPath is required"
            ));
        }

        var result = extractionService.getLatestExtraction(projectPath, templateName, userId);
        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "status", "not_found",
                "template", templateName,
                "message", "No extraction found"
            ));
        }

        ObservationEntity obs = result.get();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "template", templateName,
            "sessionId", obs.getContentSessionId(),
            "extractedData", obs.getExtractedData() != null ? obs.getExtractedData() : Map.of(),
            "createdAt", obs.getCreatedAtEpoch(),
            "observationId", obs.getId().toString()
        ));
    }

    /**
     * Get extraction history for a template.
     * GET /api/extraction/{templateName}/history?projectPath=/path&userId=alice&limit=10
     */
    @GetMapping("/{templateName}/history")
    public ResponseEntity<?> getExtractionHistory(
            @PathVariable String templateName,
            @RequestParam String projectPath,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "10") int limit) {

        if (templateName == null || templateName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "templateName is required"));
        }
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }
        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;

        List<ObservationEntity> history = extractionService.getExtractionHistory(
            projectPath, templateName, userId, limit);

        List<Map<String, Object>> result = history.stream().map(obs -> Map.<String, Object>of(
            "sessionId", obs.getContentSessionId(),
            "extractedData", obs.getExtractedData() != null ? obs.getExtractedData() : Map.of(),
            "createdAt", obs.getCreatedAtEpoch(),
            "observationId", obs.getId().toString()
        )).toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Manually trigger extraction for a project.
     * POST /api/extraction/run?projectPath=/path
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> triggerExtraction(@RequestParam String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "projectPath is required"
            ));
        }
        log.info("Manual extraction triggered for project: {}", projectPath);
        extractionService.runExtraction(projectPath);
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "projectPath", projectPath,
            "message", "Extraction completed"
        ));
    }
}
