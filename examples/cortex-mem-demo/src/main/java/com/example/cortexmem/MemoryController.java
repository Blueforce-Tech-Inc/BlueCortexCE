package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import com.ablueforce.cortexce.client.dto.QualityDistribution;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ObservationUpdate;
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
        try {
            return ResponseEntity.ok(retrievalService.retrieveExperiences(task, resolveProject(project), count));
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

    /**
     * Trigger structured extraction for a project.
     * Demonstrates Phase 3 manual extraction trigger.
     *
     * @param project Project path
     */
    @PostMapping("/memory/extract")
    public ResponseEntity<Map<String, Object>> triggerExtraction(@RequestParam(defaultValue = "/") String project) {
        try {
            String path = resolveProject(project);
            cortexClient.triggerExtraction(path);
            return ResponseEntity.ok(Map.of("status", "extraction triggered", "project", path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Extraction failed: " + e.getMessage()));
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
    public ResponseEntity<ICLPromptResult> getIclPromptTruncated(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(defaultValue = "4000") int maxChars) {
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
                    .body(new ICLPromptResult("", 0));
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
        try {
            ExperienceRequest request = ExperienceRequest.builder()
                .task(task)
                .project(resolveProject(project))
                .source(source)
                .requiredConcepts(requiredConcepts)
                .count(count)
                .build();
            return ResponseEntity.ok(cortexClient.retrieveExperiences(request));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve filtered experiences: " + e.getMessage()));
        }
    }

    // ===== V15: Extraction API (Phase 3) =====

    /**
     * Get latest extraction result for a user and template.
     * Demonstrates V15 StructuredExtractionService integration.
     *
     * @param project Project path
     * @param template Template name (e.g., "user_preferences")
     * @param userId User identifier for multi-user isolation
     */
    @GetMapping("/memory/extraction/latest")
    public ResponseEntity<Map<String, Object>> getLatestExtraction(
            @RequestParam(defaultValue = "/") String project,
            @RequestParam String template,
            @RequestParam String userId) {
        try {
            Map<String, Object> result = cortexClient.getLatestExtraction(resolveProject(project), template, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Get latest extraction failed: " + e.getMessage()));
        }
    }

    /**
     * Get extraction history for a user and template.
     * Demonstrates V15 extraction history tracking.
     *
     * @param project Project path
     * @param template Template name
     * @param userId User identifier
     * @param limit Maximum history entries (default 10)
     */
    @GetMapping("/memory/extraction/history")
    public ResponseEntity<?> getExtractionHistory(
            @RequestParam(defaultValue = "/") String project,
            @RequestParam String template,
            @RequestParam String userId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> result = cortexClient.getExtractionHistory(resolveProject(project), template, userId, limit);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get extraction history: " + e.getMessage()));
        }
    }

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
                "sample_retrieval", experiences.size() > 0 ? "working" : "empty"
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
