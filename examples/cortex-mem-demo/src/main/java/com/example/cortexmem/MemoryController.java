package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import com.ablueforce.cortexce.client.dto.QualityDistribution;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ObservationUpdate;
import com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService;
import com.ablueforce.cortexce.client.dto.Experience;
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
    public List<Experience> getExperiences(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(defaultValue = "4") int count) {
        return retrievalService.retrieveExperiences(task, resolveProject(project), count);
    }

    @GetMapping("/memory/icl")
    public String getIclPrompt(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project) {
        return retrievalService.buildICLPrompt(task, resolveProject(project));
    }

    @GetMapping("/memory/quality")
    public QualityDistribution getQuality(@RequestParam(defaultValue = "/") String project) {
        return cortexClient.getQualityDistribution(resolveProject(project));
    }

    @GetMapping("/memory/refine")
    public String triggerRefine(@RequestParam(defaultValue = "/") String project) {
        String path = resolveProject(project);
        cortexClient.triggerRefinement(path);
        return "Refinement triggered for " + path;
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
    public ICLPromptResult getIclPromptTruncated(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(defaultValue = "4000") int maxChars) {
        
        return cortexClient.buildICLPrompt(ICLPromptRequest.builder()
            .task(task)
            .project(resolveProject(project))
            .maxChars(maxChars)
            .build());
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
    public List<Experience> getExperiencesFiltered(
            @RequestParam String task,
            @RequestParam(defaultValue = "/") String project,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) List<String> requiredConcepts,
            @RequestParam(defaultValue = "4") int count) {
        
        ExperienceRequest request = ExperienceRequest.builder()
            .task(task)
            .project(resolveProject(project))
            .source(source)
            .requiredConcepts(requiredConcepts)
            .count(count)
            .build();
        
        return cortexClient.retrieveExperiences(request);
    }

    /**
     * Memory health check.
     * Demonstrates V14 observation management.
     */
    @GetMapping("/memory/health")
    public Map<String, Object> getMemoryHealth(@RequestParam(defaultValue = "/") String project) {
        try {
            List<Experience> experiences = cortexClient.retrieveExperiences(
                ExperienceRequest.builder()
                    .task("health check")
                    .project(resolveProject(project))
                    .count(1)
                    .build());
            
            return Map.of(
                "status", "ok",
                "project", resolveProject(project),
                "sample_retrieval", experiences.size() >= 0 ? "working" : "empty"
            );
        } catch (Exception e) {
            return Map.of(
                "status", "error",
                "project", resolveProject(project),
                "error", e.getMessage()
            );
        }
    }
}
