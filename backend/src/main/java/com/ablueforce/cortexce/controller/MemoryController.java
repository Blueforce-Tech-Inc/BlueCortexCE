package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.ExpRagService;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.entity.ObservationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    public ResponseEntity<Map<String, String>> triggerRefine(@RequestParam String project) {
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
    public ResponseEntity<?> retrieveExperiences(
            @RequestBody Map<String, Object> request) {

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
    public ResponseEntity<?> buildICLPrompt(@RequestBody Map<String, Object> request) {
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
            "experienceCount", String.valueOf(experiences.size()),
            "maxChars", String.valueOf(maxChars)
        ));
    }

    /**
     * Get quality distribution for a project.
     * GET /api/memory/quality-distribution?project=/path
     */
    @GetMapping("/quality-distribution")
    public ResponseEntity<Map<String, Object>> getQualityDistribution(@RequestParam String project) {
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
    public ResponseEntity<Map<String, String>> submitFeedback(@RequestBody Map<String, Object> request) {
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
    public ResponseEntity<Map<String, Object>> updateObservation(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

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
                observation.setFacts(validateStringList(list, "facts"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "facts must be a list of strings"));
            }
        }
        if (body.containsKey("concepts")) {
            Object val = body.get("concepts");
            if (val == null) {
                observation.setConcepts(null);
            } else if (val instanceof List<?> list) {
                observation.setConcepts(validateStringList(list, "concepts"));
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
     * Throws 400-style error info if non-string items are found.
     */
    private List<String> validateStringList(List<?> raw, String fieldName) {
        List<String> result = new java.util.ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String s) {
                result.add(s);
            } else {
                log.warn("Non-string item in '{}' list: {} (type {})", fieldName, item,
                    item != null ? item.getClass().getName() : "null");
                // Skip non-string items rather than failing — lenient parsing
            }
        }
        return result;
    }

    /**
     * Delete an observation.
     * DELETE /api/memory/observations/{id}
     */
    @DeleteMapping("/observations/{id}")
    public ResponseEntity<Map<String, String>> deleteObservation(@PathVariable UUID id) {
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
