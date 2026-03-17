package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.ExpRagService;
import com.ablueforce.cortexce.service.QualityScorer;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
     * Body: {"task": "current task description", "project": "/path", "count": 4}
     */
    @PostMapping("/experiences")
    public ResponseEntity<List<ExpRagService.Experience>> retrieveExperiences(
            @RequestBody Map<String, Object> request) {
        
        String task = (String) request.get("task");
        String project = (String) request.get("project");
        int count = request.get("count") != null ? (Integer) request.get("count") : 4;
        
        List<ExpRagService.Experience> experiences = expRagService
            .retrieveExperiences(task, project, count);
        
        return ResponseEntity.ok(experiences);
    }

    /**
     * Build ICL prompt from experiences.
     * POST /api/memory/icl-prompt
     */
    @PostMapping("/icl-prompt")
    public ResponseEntity<Map<String, String>> buildICLPrompt(@RequestBody Map<String, Object> request) {
        String task = (String) request.get("task");
        String project = (String) request.get("project");
        
        List<ExpRagService.Experience> experiences = expRagService
            .retrieveExperiences(task, project, 4);
        
        String prompt = expRagService.buildICLPrompt(task, experiences);
        
        return ResponseEntity.ok(Map.of(
            "prompt", prompt,
            "experienceCount", String.valueOf(experiences.size())
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
}
