package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.service.StructuredExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Extraction", description = "Structured extraction API for Phase 3 — query extraction results by template name, retrieve history, and manually trigger extraction runs")
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
    @Operation(summary = "Get latest extraction result",
        description = "Returns the most recent extraction result for a given template name and project. Useful for retrieving user preferences, allergies, or other structured data extracted from recent sessions. Returns status 'not_found' if no extraction exists.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Latest extraction result (or not_found status if none exists)"),
        @ApiResponse(responseCode = "400", description = "Missing required parameter: projectPath"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve extraction due to internal error")
    })
    public ResponseEntity<Map<String, Object>> getLatestExtraction(
            @Parameter(description = "Extraction template name (e.g., 'user-preferences', 'allergy-info')", required = true, example = "user-preferences")
            @PathVariable String templateName,
            @Parameter(description = "Absolute project path", required = true, example = "/Users/dev/my-project")
            @RequestParam String projectPath,
            @Parameter(description = "Optional user ID for user-scoped extractions", required = false, example = "alice")
            @RequestParam(required = false) String userId) {

        // templateName from @PathVariable is never null (Spring returns 404 before this method)
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "projectPath is required"
            ));
        }

        try {
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
        } catch (Exception e) {
            log.error("Failed to get latest extraction for template {}: {}", templateName, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get extraction: " + e.getMessage()
            ));
        }
    }

    /**
     * Get extraction history for a template.
     * GET /api/extraction/{templateName}/history?projectPath=/path&userId=alice&limit=10
     */
    @GetMapping("/{templateName}/history")
    @Operation(summary = "Get extraction history",
        description = "Returns historical extraction results for a given template name and project. Results are returned in reverse chronological order. The limit is clamped between 1 and 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Extraction history retrieved"),
        @ApiResponse(responseCode = "400", description = "Missing required parameter: projectPath"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve extraction history due to internal error")
    })
    public ResponseEntity<?> getExtractionHistory(
            @Parameter(description = "Extraction template name", required = true, example = "user-preferences")
            @PathVariable String templateName,
            @Parameter(description = "Absolute project path", required = true, example = "/Users/dev/my-project")
            @RequestParam String projectPath,
            @Parameter(description = "Optional user ID for user-scoped extractions", required = false, example = "alice")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Maximum number of history entries to return (1-100, default 10)", required = false, example = "10")
            @RequestParam(defaultValue = "10") int limit) {

        // templateName from @PathVariable is never null
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectPath is required"));
        }
        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;

        try {
            List<ObservationEntity> history = extractionService.getExtractionHistory(
                projectPath, templateName, userId, limit);

            List<Map<String, Object>> result = history.stream().map(obs -> Map.<String, Object>of(
                "sessionId", obs.getContentSessionId(),
                "extractedData", obs.getExtractedData() != null ? obs.getExtractedData() : Map.of(),
                "createdAt", obs.getCreatedAtEpoch(),
                "observationId", obs.getId().toString()
            )).toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get extraction history for template {}: {}", templateName, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get extraction history: " + e.getMessage()
            ));
        }
    }

    /**
     * Manually trigger extraction for a project.
     * POST /api/extraction/run?projectPath=/path
     */
    @PostMapping("/run")
    @Operation(summary = "Manually trigger extraction",
        description = "Manually triggers the structured extraction pipeline for a project. Runs asynchronously. Extraction uses the configured templates to extract structured data (e.g., user preferences, allergies) from recent session observations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Extraction triggered successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required parameter: projectPath"),
        @ApiResponse(responseCode = "500", description = "Failed to trigger extraction due to internal error")
    })
    public ResponseEntity<Map<String, String>> triggerExtraction(
            @Parameter(description = "Absolute project path to run extraction for", required = true, example = "/Users/dev/my-project")
            @RequestParam String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "projectPath is required"
            ));
        }

        try {
            log.info("Manual extraction triggered for project: {}", projectPath);
            extractionService.runExtraction(projectPath);
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "projectPath", projectPath,
                "message", "Extraction completed"
            ));
        } catch (Exception e) {
            log.error("Failed to trigger extraction for project {}: {}", projectPath, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to trigger extraction: " + e.getMessage()
            ));
        }
    }
}
