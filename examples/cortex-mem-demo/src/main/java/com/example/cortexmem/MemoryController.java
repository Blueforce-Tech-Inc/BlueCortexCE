package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService;
import com.ablueforce.cortexce.client.dto.Experience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Memory retrieval and management — scoped by project.
 *
 * <p>project may be a demo.projects key or absolute path.
 * 
 * <p>V14 Features demonstrated:
 * <ul>
 *   <li>Source attribution for observations</li>
 *   <li>Structured extractedData for key-value preferences</li>
 *   <li>Adaptive truncation via maxChars</li>
 *   <li>Source-based and concept-based filtering</li>
 *   <li>Memory update and delete operations</li>
 * </ul>
 */
@RestController
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryRetrievalService retrievalService;
    private final CortexMemClient cortexClient;
    private final DemoProperties demoProperties;

    public MemoryController(MemoryRetrievalService retrievalService, CortexMemClient cortexClient,
                            DemoProperties demoProperties) {
        this.retrievalService = retrievalService;
        this.cortexClient = cortexClient;
        this.demoProperties = demoProperties;
    }

    private String resolveProject(String project) {
        if (project == null || project.isBlank()) return "/";
        String resolved = demoProperties.resolveProjectPath(project);
        return resolved != null ? resolved : project;
    }

    // ===== Basic Memory Operations =====

    @GetMapping("/memory/experiences")
    public ResponseEntity<?> getExperiences(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(defaultValue = "4") int count) {
        if (count < 0 || count > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "count must be between 0 and 100"));
        }
        try {
            // count=0 means "use default" (consistent with ObservationsController limit=0)
            int effectiveCount = count > 0 ? count : 4;
            return ResponseEntity.ok(retrievalService.retrieveExperiences(task, resolveProject(project), effectiveCount));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve experiences: " + e.getMessage()));
        }
    }

    @GetMapping("/memory/icl")
    public ResponseEntity<?> getIclPrompt(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project) {
        try {
            return ResponseEntity.ok(retrievalService.buildICLPrompt(task, resolveProject(project)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "ICL prompt failed: " + e.getMessage()));
        }
    }

    @GetMapping("/memory/quality")
    public ResponseEntity<?> getQuality(@RequestParam(defaultValue = "/") String project) {
        try {
            return ResponseEntity.ok(cortexClient.getQualityDistribution(resolveProject(project)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get quality distribution: " + e.getMessage()));
        }
    }

    @PostMapping("/memory/refine")
    public ResponseEntity<Map<String, Object>> triggerRefine(@RequestParam(defaultValue = "/") String project) {
        try {
            String path = resolveProject(project);
            cortexClient.triggerRefinement(path);
            return ResponseEntity.ok(Map.of("status", "refinement triggered", "project", path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Refinement failed: " + e.getMessage()));
        }
    }

    // ===== V14: Advanced Features =====

    /**
     * Get ICL prompt with adaptive truncation.
     * Demonstrates V14 maxChars parameter.
     * 
     * @param task The current task
     * @param project Project path
     * @param maxChars Maximum characters (default 4000). 
     *                 Configure based on your model's context window:
     *                 - 128K models: 8000-12000
     *                 - 32K models: 4000-6000
     *                 - 8K models: 2000-3000
     */
    @GetMapping("/memory/icl/truncated")
    public ResponseEntity<?> getIclPromptTruncated(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(defaultValue = "4000") int maxChars) {
        if (maxChars < 1 || maxChars > 100000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "maxChars must be between 1 and 100000"));
        }
        String projectPath = resolveProject(project);
        try {
            return ResponseEntity.ok(cortexClient.buildICLPrompt(ICLPromptRequest.builder()
                .task(task)
                .project(projectPath)
                .maxChars(maxChars)
                .build()));
        } catch (Exception e) {
            log.error("ICL prompt truncated failed for project={}", projectPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "ICL prompt failed: " + e.getMessage()));
        }
    }

    /**
     * Get experiences with source filtering.
     * Demonstrates V14 source attribution feature.
     * 
     * @param task Task description
     * @param project Project path
     * @param source Filter by source (e.g., "tool_result", "user_statement", "llm_inference", "manual")
     * @param requiredConcepts Filter to experiences containing these concepts
     * @param count Number of experiences
     */
    @GetMapping("/memory/experiences/filtered")
    public ResponseEntity<?> getExperiencesFiltered(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) List<String> requiredConcepts,
            @RequestParam(defaultValue = "4") int count) {
        if (count < 0 || count > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "count must be between 0 and 100"));
        }
        try {
            int effectiveCount = count > 0 ? count : 4;
            ExperienceRequest request = ExperienceRequest.builder()
                .task(task)
                .project(resolveProject(project))
                .source(source)
                .requiredConcepts(requiredConcepts)
                .count(effectiveCount)
                .build();
            return ResponseEntity.ok(cortexClient.retrieveExperiences(request));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve filtered experiences: " + e.getMessage()));
        }
    }

    // ===== V15: Extraction API (Phase 3) =====
    // Extraction endpoints moved to ExtractionController (/demo/extraction/*)
    // to avoid duplication. Use /demo/extraction/latest, /demo/extraction/history, /demo/extraction/run.

    /**
     * Memory health check.
     * Demonstrates V14 observation management.
     */
    @GetMapping("/memory/health")
    public ResponseEntity<Map<String, Object>> getMemoryHealth(@RequestParam(defaultValue = "/") String project) {
        String projectPath = resolveProject(project);
        try {
            List<Experience> experiences = cortexClient.retrieveExperiences(
                ExperienceRequest.builder()
                    .task("health check")
                    .project(projectPath)
                    .count(1)
                    .build());

            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "project", projectPath,
                "sample_retrieval", !experiences.isEmpty() ? "working" : "empty"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "project", projectPath,
                "error", e.getMessage()
            ));
        }
    }
}
