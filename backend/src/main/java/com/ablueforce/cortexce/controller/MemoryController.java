package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.ExpRagService;
import com.ablueforce.cortexce.service.QualityScorer;
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
    private final QualityScorer qualityScorer;
    private final MemoryRefineEventPublisher eventPublisher;

    public MemoryController(MemoryRefineService memoryRefineService,
                          ExpRagService expRagService,
                          ObservationRepository observationRepository,
                          QualityScorer qualityScorer,
                          MemoryRefineEventPublisher eventPublisher) {
        this.memoryRefineService = memoryRefineService;
        this.expRagService = expRagService;
        this.observationRepository = observationRepository;
        this.qualityScorer = qualityScorer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Trigger memory refinement for a project.
     * POST /api/memory/refine?project=/path/to/project
     */
    @PostMapping("/refine")
    public ResponseEntity<Map<String, String>> triggerRefine(@RequestParam String project) {
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
    public ResponseEntity<List<ExpRagService.Experience>> retrieveExperiences(
            @RequestBody Map<String, Object> request) {
        
        String task = (String) request.get("task");
        String project = (String) request.get("project");
        int count = request.get("count") != null ? (Integer) request.get("count") : 4;
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
    public ResponseEntity<Map<String, String>> buildICLPrompt(@RequestBody Map<String, Object> request) {
        String task = (String) request.get("task");
        String project = (String) request.get("project");
        int maxChars = request.get("maxChars") != null 
            ? ((Number) request.get("maxChars")).intValue() 
            : 4000;
        
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
        // This endpoint would need implementation with observation ID lookup
        // Simplified for now
        return ResponseEntity.ok(Map.of(
            "status", "received",
            "message", "Feedback submission endpoint - requires observation ID lookup"
        ));
    }

    // ==================== Observation Management (V14) ====================

    /**
     * Update an existing observation.
     * PATCH /api/memory/observations/{id}
     * Body: {"title": "...", "content": "...", "facts": [...], "concepts": [...], "source": "...", "extractedData": {...}}
     */
    @PatchMapping("/observations/{id}")
    public ResponseEntity<Map<String, Object>> updateObservation(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {

        ObservationEntity observation = observationRepository.findById(id).orElse(null);
        if (observation == null) {
            return ResponseEntity.notFound().build();
        }

        // Update fields if present
        if (body.containsKey("title")) {
            observation.setTitle((String) body.get("title"));
        }
        if (body.containsKey("content") || body.containsKey("narrative")) {
            observation.setContent((String) body.getOrDefault("content", body.get("narrative")));
        }
        if (body.containsKey("subtitle")) {
            observation.setSubtitle((String) body.get("subtitle"));
        }
        if (body.containsKey("facts")) {
            observation.setFacts(safeGetStringList(body, "facts"));
        }
        if (body.containsKey("concepts")) {
            observation.setConcepts(safeGetStringList(body, "concepts"));
        }
        if (body.containsKey("source")) {
            observation.setSource((String) body.get("source"));
        }
        if (body.containsKey("extractedData")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extractedData = (Map<String, Object>) body.get("extractedData");
            observation.setExtractedData(extractedData);
        }

        ObservationEntity saved = observationRepository.save(observation);
        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "id", saved.getId().toString()
        ));
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

    // Helper: safely extract List<String> from request body
    @SuppressWarnings("unchecked")
    private List<String> safeGetStringList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return List.of();
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .filter(item -> item instanceof String)
                .map(item -> (String) item)
                .toList();
        }
        return List.of();
    }
}
