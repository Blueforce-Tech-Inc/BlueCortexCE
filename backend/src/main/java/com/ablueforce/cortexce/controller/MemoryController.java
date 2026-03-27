package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.ExpRagService;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.entity.ObservationEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ReMem API Controller - External integration interface.
 * 
 * Based on Evo-Memory paper Section 6.2 - Pseudo-synchronous API for external agents.
 * 
 * Provides REST endpoints for external Agent frameworks to:
 * - Trigger memory refinement
 * - Retrieve experiences for ICL
 * - Query quality distribution
 */
@RestController
@RequestMapping("/api/memory")
@Tag(name = "Memory", description = "ReMem API for memory refinement, experience retrieval, and ICL prompt building. Based on Evo-Memory paper Section 6.2.")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryRefineService memoryRefineService;
    private final ExpRagService expRagService;
    private final ObservationRepository observationRepository;
    private final MemoryRefineEventPublisher eventPublisher;

    public MemoryController(MemoryRefineService memoryRefineService,
                          ExpRagService expRagService,
                          ObservationRepository observationRepository,
                          MemoryRefineEventPublisher eventPublisher) {
        this.memoryRefineService = memoryRefineService;
        this.expRagService = expRagService;
        this.observationRepository = observationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Trigger memory refinement for a project.
     * POST /api/memory/refine?project=/path/to/project
     */
    @PostMapping("/refine")
    @Operation(summary = "Trigger memory refinement",
        description = "Publishes a memory refinement event for async processing. Refinement re-evaluates observation quality and updates the quality distribution for the project.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Refinement event published successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required parameter: project")
    })
    public ResponseEntity<Map<String, String>> triggerRefine(
            @Parameter(description = "Absolute project path to trigger refinement for", required = true, example = "/Users/dev/my-project")
            @RequestParam String project) {
        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "project is required"));
        }
        // Publish event for async processing
        eventPublisher.publishManualRefineEvent(project);
        return ResponseEntity.ok(Map.of(
            "status", "triggered",
            "project", project,
            "message", "Memory refinement event has been published"
        ));
    }

    /**
     * Retrieve experiences for ICL context.
     * POST /api/memory/experiences
     * Body: {"task": "...", "project": "/path", "count": 4, "source": "optional", "requiredConcepts": ["optional"]}
     */
    @PostMapping("/experiences")
    @Operation(summary = "Retrieve experiences for ICL",
        description = "Retrieves relevant past experiences (observations) for in-context learning. Uses vector similarity search against the task description. Optionally filters by source and required concepts. Returns ordered list of relevant experiences.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Experiences retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required field: task")
    })
    public ResponseEntity<?> retrieveExperiences(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with task (required), project, count (default 4), source, and requiredConcepts", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {

        String task = (String) request.get("task");
        if (task == null || task.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "task is required"));
        }
        String project = (String) request.get("project");
        int count = request.get("count") != null ? ((Number) request.get("count")).intValue() : 4;
        String source = (String) request.get("source");
        @SuppressWarnings("unchecked")
        List<String> requiredConcepts = (List<String>) request.get("requiredConcepts");
        
        List<ExpRagService.Experience> experiences = expRagService
            .retrieveExperiences(task, project, count, source, requiredConcepts);
        
        return ResponseEntity.ok(experiences);
    }

    /**
     * Build ICL prompt from experiences.
     * POST /api/memory/icl-prompt
     * Body: {"task": "...", "project": "...", "maxChars": 4000}
     */
    @PostMapping("/icl-prompt")
    @Operation(summary = "Build ICL prompt from experiences",
        description = "Retrieves relevant experiences and formats them as an in-context learning (ICL) prompt. The prompt is constructed by combining the task description with the retrieved experiences, truncated to maxChars.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ICL prompt built successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required field: task")
    })
    public ResponseEntity<?> buildICLPrompt(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with task (required), project, and maxChars (default 4000)", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        String task = (String) request.get("task");
        if (task == null || task.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "task is required"));
        }
        String project = (String) request.get("project");
        int maxChars = 4000;
        Object maxCharsObj = request.get("maxChars");
        if (maxCharsObj instanceof Number n) {
            maxChars = Math.max(100, n.intValue());
        }

        List<ExpRagService.Experience> experiences = expRagService
            .retrieveExperiences(task, project, 4);
        
        String prompt = expRagService.buildICLPrompt(task, experiences, maxChars);
        
        return ResponseEntity.ok(Map.of(
            "prompt", prompt,
            "experienceCount", experiences.size(),
            "maxChars", maxChars
        ));
    }

    /**
     * Get quality distribution for a project.
     * GET /api/memory/quality-distribution?project=/path
     */
    @GetMapping("/quality-distribution")
    @Operation(summary = "Get quality distribution",
        description = "Returns the quality distribution (high/medium/low/unknown counts) for observations in a project. Used by WebUI quality charts and memory refinement monitoring.")
    @ApiResponse(responseCode = "200", description = "Quality distribution retrieved (returns zeros if no data)")
    public ResponseEntity<Map<String, Object>> getQualityDistribution(
            @Parameter(description = "Absolute project path to query quality distribution for", required = true, example = "/Users/dev/my-project")
            @RequestParam String project) {
        try {
            Object[] distribution = observationRepository.getQualityDistribution(project);
            
            if (distribution == null || distribution.length < 4) {
                return ResponseEntity.ok(Map.of(
                    "project", project,
                    "high", 0L,
                    "medium", 0L,
                    "low", 0L,
                    "unknown", 0L
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "project", project,
                "high", distribution[0],
                "medium", distribution[1],
                "low", distribution[2],
                "unknown", distribution[3]
            ));
        } catch (Exception e) {
            log.error("Failed to get quality distribution", e);
            return ResponseEntity.ok(Map.of(
                "project", project,
                "high", 0L,
                "medium", 0L,
                "low", 0L,
                "unknown", 0L,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Manual feedback submission (WebUI).
     * POST /api/memory/feedback
     * Body: {"observationId": "uuid", "feedbackType": "SUCCESS", "comment": "optional"}
     */
    @PostMapping("/feedback")
    @Operation(summary = "Submit manual feedback (not yet implemented)",
        description = "Allows manual feedback submission for observations via WebUI. Currently returns 501 Not Implemented.")
    @ApiResponse(responseCode = "501", description = "Feedback submission endpoint is not yet implemented")
    public ResponseEntity<Map<String, String>> submitFeedback(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with observationId (UUID), feedbackType (e.g., SUCCESS), and optional comment", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        // TODO: Full implementation requires observation ID lookup and feedback persistence
        return ResponseEntity.status(501).body(Map.of(
            "status", "not_implemented",
            "message", "Feedback submission endpoint is not yet implemented"
        ));
    }

    // ==================== Observation Management (V14) ====================

    /**
     * Update an existing observation.
     * PATCH /api/memory/observations/{id}
     * Body: {"title": "...", "content": "...", "facts": [...], "concepts": [...], "source": "...", "extractedData": {...}}
     *
     * Null values in the body are ignored (field left unchanged).
     * Invalid types return 400 Bad Request to prevent silent data loss.
     */
    @PatchMapping("/observations/{id}")
    @Operation(summary = "Update an observation (V14)",
        description = "Partially updates an existing observation. Only fields present in the request body are updated; null values clear the field, absent fields are left unchanged. Supports: title, content/narrative, subtitle, source, facts, concepts, extractedData. Returns 404 if observation not found.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Observation updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid field types in request body"),
        @ApiResponse(responseCode = "404", description = "Observation with given UUID not found")
    })
    public ResponseEntity<Map<String, Object>> updateObservation(
            @Parameter(description = "UUID of the observation to update", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Partial update body. Supports: title, content/narrative, subtitle, source, facts (list of strings), concepts (list of strings), extractedData (JSON object). Null = clear field, absent = no change.", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {

        ObservationEntity observation = observationRepository.findById(id).orElse(null);
        if (observation == null) {
            return ResponseEntity.notFound().build();
        }

        // Update string fields — explicit null means "clear", absent means "skip"
        if (body.containsKey("title")) {
            Object val = body.get("title");
            if (val == null) {
                observation.setTitle(null);
            } else if (val instanceof String s) {
                observation.setTitle(s);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "title must be a string"));
            }
        }
        if (body.containsKey("content") || body.containsKey("narrative")) {
            Object val = body.getOrDefault("content", body.get("narrative"));
            if (val == null) {
                observation.setContent(null);
            } else if (val instanceof String s) {
                observation.setContent(s);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "content/narrative must be a string"));
            }
        }
        if (body.containsKey("subtitle")) {
            Object val = body.get("subtitle");
            if (val == null) {
                observation.setSubtitle(null);
            } else if (val instanceof String s) {
                observation.setSubtitle(s);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "subtitle must be a string"));
            }
        }
        if (body.containsKey("source")) {
            Object val = body.get("source");
            if (val == null) {
                observation.setSource(null);
            } else if (val instanceof String s) {
                observation.setSource(s);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "source must be a string"));
            }
        }

        // Update list fields — null means "clear", wrong type → 400
        if (body.containsKey("facts")) {
            Object val = body.get("facts");
            if (val == null) {
                observation.setFacts(null);
            } else if (val instanceof List<?> list) {
                var result = validateStringList(list, "facts");
                if (result == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "facts must be a list of strings"));
                }
                observation.setFacts(result);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "facts must be a list of strings"));
            }
        }
        if (body.containsKey("concepts")) {
            Object val = body.get("concepts");
            if (val == null) {
                observation.setConcepts(null);
            } else if (val instanceof List<?> list) {
                var result = validateStringList(list, "concepts");
                if (result == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "concepts must be a list of strings"));
                }
                observation.setConcepts(result);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "concepts must be a list of strings"));
            }
        }

        // Update map fields — null means "clear", wrong type → 400
        if (body.containsKey("extractedData")) {
            Object val = body.get("extractedData");
            if (val == null) {
                observation.setExtractedData(null);
            } else if (val instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> extractedData = (Map<String, Object>) val;
                observation.setExtractedData(extractedData);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "extractedData must be a JSON object"));
            }
        }

        ObservationEntity saved = observationRepository.save(observation);
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "id", saved.getId().toString()
        ));
    }

    /**
     * Validate that all items in a list are strings.
     * Returns the validated list if all items are strings, or null if any non-string item is found.
     * Caller should return 400 Bad Request when null is returned.
     */
    private List<String> validateStringList(List<?> raw, String fieldName) {
        List<String> result = new java.util.ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String s) {
                result.add(s);
            } else {
                log.warn("Non-string item in '{}' list: {} (type {})", fieldName, item,
                    item != null ? item.getClass().getName() : "null");
                return null; // Fail-fast: caller returns 400
            }
        }
        return result;
    }

    /**
     * Delete an observation.
     * DELETE /api/memory/observations/{id}
     */
    @DeleteMapping("/observations/{id}")
    @Operation(summary = "Delete an observation",
        description = "Permanently deletes an observation by its UUID. Returns 404 if the observation does not exist.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Observation deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Observation with given UUID not found")
    })
    public ResponseEntity<Map<String, String>> deleteObservation(
            @Parameter(description = "UUID of the observation to delete", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        if (!observationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        observationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of(
            "status", "deleted",
            "id", id.toString()
        ));
    }

}
