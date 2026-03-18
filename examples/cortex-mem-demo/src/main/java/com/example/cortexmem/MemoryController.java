package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.QualityDistribution;
import com.ablueforce.cortexce.ai.retrieval.MemoryRetrievalService;
import com.ablueforce.cortexce.client.dto.Experience;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Memory retrieval and management — scoped by project.
 *
 * <p>project may be a demo.projects key or absolute path.
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
}
