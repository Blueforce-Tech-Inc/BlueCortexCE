package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.common.ErrorResponses;
import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.ExpRagService;
import com.ablueforce.cortexce.service.ObservationManagementService;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
    private final ObservationManagementService observationManagementService;
    private final MemoryRefineEventPublisher eventPublisher;

    public MemoryController(MemoryRefineService memoryRefineService,
                          ExpRagService expRagService,
                          ObservationManagementService observationManagementService,
                          MemoryRefineEventPublisher eventPublisher) {
        this.memoryRefineService = memoryRefineService;
        this.expRagService = expRagService;
        this.observationManagementService = observationManagementService;
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
        @ApiResponse(responseCode = "200", description = "Refinement event published successfully",
            content = @Content(schema = @Schema(type = "object", example = "{\"status\":\"triggered\",\"project\":\"/path\",\"message\":\"Memory refinement event has been published\"}"))),
        @ApiResponse(responseCode = "400", description = "Missing required parameter: project",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\":\"project is required\"}")))
    })
    public ResponseEntity<Object> triggerRefine(
            @Parameter(description = "Absolute project path to trigger refinement for", required = true, example = "/Users/dev/my-project")
            @RequestParam String project) {
        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "project is required"));
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
        @ApiResponse(responseCode = "200", description = "Experiences retrieved successfully",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = com.ablueforce.cortexce.service.ExpRagService.Experience.class)))),
        @ApiResponse(responseCode = "400", description = "Missing required field: task",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class)))
    })
    public ResponseEntity<Object> retrieveExperiences(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Experience retrieval request",
                required = true,
                content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiRequests.ExperienceRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody com.ablueforce.cortexce.dto.ApiRequests.ExperienceRequest request) {

        String task = request.task();
        if (task == null || task.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "task is required"));
        }
        String project = request.project();
        int count = request.count() != null ? request.count() : 4;
        String source = request.source();
        List<String> requiredConcepts = request.requiredConcepts();
        String userId = request.userId(); // Phase 3: multi-user isolation
        
        List<ExpRagService.Experience> experiences = expRagService
            .retrieveExperiences(task, project, count, source, requiredConcepts, userId);
        
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
        @ApiResponse(responseCode = "200", description = "ICL prompt built successfully",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ICLPromptResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing required field: task",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class)))
    })
    public ResponseEntity<Object> buildICLPrompt(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "ICL prompt build request",
                required = true,
                content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiRequests.ICLPromptRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody com.ablueforce.cortexce.dto.ApiRequests.ICLPromptRequest request) {
        String task = request.task();
        if (task == null || task.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "task is required"));
        }
        String project = request.project();
        int maxChars = request.maxChars() != null ? Math.max(100, request.maxChars()) : 4000;
        String userId = request.userId(); // Phase 3: multi-user isolation

        List<ExpRagService.Experience> experiences = expRagService
            .retrieveExperiences(task, project, 4, null, null, userId);
        
        String prompt = expRagService.buildICLPrompt(task, experiences, maxChars);
        
        return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.ICLPromptResponse(
            prompt, experiences.size(), maxChars
        ));
    }

    /**
     * Get quality distribution for a project.
     * GET /api/memory/quality-distribution?project=/path
     */
    @GetMapping("/quality-distribution")
    @Operation(summary = "Get quality distribution",
        description = "Returns the quality distribution (high/medium/low/unknown counts) for observations in a project. Used by WebUI quality charts and memory refinement monitoring.")
    @ApiResponse(responseCode = "200", description = "Quality distribution retrieved (returns zeros if no data)",
        content = @Content(schema = @Schema(type = "object", example = "{\"project\":\"/path\",\"high\":10,\"medium\":20,\"low\":5,\"unknown\":3}")))
    @ApiResponse(responseCode = "500", description = "Database error retrieving quality distribution",
        content = @Content(schema = @Schema(type = "object", example = "{\"project\":\"/path\",\"error\":\"Failed to get quality distribution: ...\"}")))
    public ResponseEntity<Map<String, Object>> getQualityDistribution(
            @Parameter(description = "Absolute project path to query quality distribution for", required = true, example = "/Users/dev/my-project")
            @RequestParam String project) {
        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "project is required"));
        }
        try {
            ObservationManagementService.QualityDistribution distribution =
                observationManagementService.qualityDistribution(project);

            return ResponseEntity.ok(Map.of(
                "project", project,
                "high", distribution.high(),
                "medium", distribution.medium(),
                "low", distribution.low(),
                "unknown", distribution.unknown()
            ));
        } catch (Exception e) {
            log.error("Failed to get quality distribution", e);
            return ResponseEntity.status(500).body(ErrorResponses.body(ErrorResponses.INTERNAL_ERROR, "Failed to get quality distribution: " + e.getMessage(), Map.of("project", project, "high", 0L, "medium", 0L, "low", 0L, "unknown", 0L)));
        }
    }

    /**
     * Manual feedback submission (WebUI).
     * POST /api/memory/feedback
     * Body: {"observationId": "uuid", "feedbackType": "SUCCESS", "comment": "optional"}
     */
    @PostMapping("/feedback")
    @Operation(summary = "Submit manual feedback for an observation",
        description = "Allows manual feedback submission for observations via WebUI. Updates feedback_type, user_comment, and feedback_updated_at on the observation.")
    @ApiResponse(responseCode = "200", description = "Feedback recorded successfully",
        content = @Content(schema = @Schema(type = "object", example = "{\"status\":\"ok\",\"observationId\":\"...\"}")))
    @ApiResponse(responseCode = "404", description = "Observation not found",
        content = @Content(schema = @Schema(type = "object", example = "{\"error\":\"Observation not found\"}")))
    @ApiResponse(responseCode = "400", description = "Invalid request (missing observationId or feedbackType)",
        content = @Content(schema = @Schema(type = "object", example = "{\"error\":\"observationId is required\"}")))
    public ResponseEntity<Object> submitFeedback(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Feedback submission request",
                required = true,
                content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiRequests.FeedbackRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody com.ablueforce.cortexce.dto.ApiRequests.FeedbackRequest request) {
        if (request.observationId() == null || request.observationId().isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "observationId is required"));
        }
        if (request.feedbackType() == null || request.feedbackType().isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "feedbackType is required"));
        }
        java.util.UUID obsId;
        try {
            obsId = java.util.UUID.fromString(request.observationId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "Invalid observationId format: " + request.observationId()));
        }
        ObservationManagementService.MutationResult result =
            observationManagementService.submitFeedback(obsId, request.feedbackType(), request.comment());
        if (result.status() == ObservationManagementService.MutationResult.Status.NOT_FOUND) {
            return ResponseEntity.status(404).body(ErrorResponses.body(ErrorResponses.NOT_FOUND, "Observation not found: " + request.observationId()));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "observationId", request.observationId()));
    }

    // ==================== Observation Management (V14) ====================

    /**
     * Update an existing observation.
     * PATCH /api/memory/observations/{id}
     * Body: {"title": "...", "content": "...", "facts": [...], "concepts": [...], "source": "...", "extractedData": {...}}
     *
     * Only fields present in the body are touched. An explicit null CLEARS the
     * field; an absent field is left unchanged. (An earlier revision of this
     * comment said nulls were ignored, which is the opposite of what the code
     * does and of what the @Operation description below states — verified live
     * by PATCHing {"content":null,"narrative":"x"}, which stored NULL. The
     * machine-readable description has always been the correct one, and it is
     * what /v3/api-docs and the generated SDKs consume. See P2-68.)
     * Invalid types return 400 Bad Request to prevent silent data loss.
     */
    @PatchMapping("/observations/{id}")
    @Operation(summary = "Update an observation (V14)",
        description = "Partially updates an existing observation. Only fields present in the request body are updated; null values clear the field, absent fields are left unchanged. Supports: title, content/narrative, subtitle, source, facts, concepts, extractedData. Returns 404 if observation not found.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Observation updated successfully",
            content = @Content(schema = @Schema(type = "object", example = "{\"status\":\"updated\",\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid field types in request body",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\":\"title must be a string\"}"))),
        @ApiResponse(responseCode = "404", description = "Observation with given UUID not found")
    })
    public ResponseEntity<Map<String, Object>> updateObservation(
            @Parameter(description = "UUID of the observation to update", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Partial update body (PATCH semantics). " +
                    "Supported fields: " +
                    "title (string, null=clear), " +
                    "content/narrative (string, null=clear), " +
                    "subtitle (string, null=clear), " +
                    "source (string, null=clear), " +
                    "facts (list of strings, null=clear), " +
                    "concepts (list of strings, null=clear), " +
                    "extractedData (JSON object, null=clear). " +
                    "Absent fields are left unchanged. Null values explicitly clear the field.",
                required = true,
                content = @Content(schema = @Schema(
                    type = "object",
                    example = "{\"title\":\"Updated title\",\"content\":\"Updated narrative\",\"concepts\":[\"how-it-works\",\"architecture\"]}")))
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {

        ObservationManagementService.MutationResult result =
            observationManagementService.updateObservation(id, body);
        return switch (result.status()) {
            case OK -> ResponseEntity.ok(Map.of(
                "status", "updated",
                "id", id.toString()
            ));
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case INVALID -> ResponseEntity.badRequest().body(
                ErrorResponses.body(ErrorResponses.INVALID_REQUEST, result.error()));
        };
    }

    /**
     * Delete an observation.
     * DELETE /api/memory/observations/{id}
     */
    @DeleteMapping("/observations/{id}")
    @Operation(summary = "Delete an observation",
        description = "Permanently deletes an observation by its UUID. Returns 404 if the observation does not exist.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Observation deleted successfully",
            content = @Content(schema = @Schema(type = "object", example = "{\"status\":\"deleted\",\"id\":\"550e8400-e29b-41d4-a716-446655440000\"}"))),
        @ApiResponse(responseCode = "404", description = "Observation with given UUID not found")
    })
    public ResponseEntity<Object> deleteObservation(
            @Parameter(description = "UUID of the observation to delete", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        if (!observationManagementService.deleteObservation(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "status", "deleted",
            "id", id.toString()
        ));
    }

}
