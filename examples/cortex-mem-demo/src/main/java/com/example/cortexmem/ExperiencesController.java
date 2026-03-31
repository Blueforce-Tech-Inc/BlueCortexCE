package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ExperienceRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * Demo controller for Experiences and ICL Prompt APIs.
 * Demonstrates experience retrieval and in-context learning prompt building.
 *
 * <p>Cross-SDK parity: Go /experiences + /iclprompt, Python /experiences + /iclprompt,
 * JS /experiences + /iclprompt.
 */
@RestController
@RequestMapping("/demo")
public class ExperiencesController {

    private static final Logger log = LoggerFactory.getLogger(ExperiencesController.class);

    private final CortexMemClient client;

    public ExperiencesController(CortexMemClient client) {
        this.client = client;
    }

    /**
     * GET /demo/experiences?project=/test&task=fix+bug&count=4&source=...&userId=...&requiredConcepts=a,b
     */
    @GetMapping(value = "/experiences", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getExperiences(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String task,
            @RequestParam(defaultValue = "4") Integer count,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String requiredConcepts) {

        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }
        if (task == null || task.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "task is required"));
        }
        if (count < 1 || count > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "count must be between 1 and 100"));
        }

        try {
            ExperienceRequest.Builder builder = ExperienceRequest.builder()
                    .project(project)
                    .task(task)
                    .count(count);

            if (source != null && !source.isBlank()) {
                builder.source(source);
            }
            if (userId != null && !userId.isBlank()) {
                builder.userId(userId);
            }
            if (requiredConcepts != null && !requiredConcepts.isBlank()) {
                List<String> concepts = java.util.Arrays.stream(requiredConcepts.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (!concepts.isEmpty()) {
                    builder.requiredConcepts(concepts);
                }
            }

            var experiences = client.retrieveExperiences(builder.build());
            return ResponseEntity.ok(Map.of(
                    "experiences", experiences,
                    "count", experiences.size()));
        } catch (Exception e) {
            log.error("Retrieve experiences failed for project={}", project, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Retrieve experiences failed: " + e.getMessage()));
        }
    }

    /**
     * GET /demo/iclprompt?project=/test&task=fix+bug&maxChars=4000&userId=alice
     */
    @GetMapping(value = "/iclprompt", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> buildICLPrompt(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String task,
            @RequestParam(defaultValue = "0") Integer maxChars,
            @RequestParam(required = false) String userId) {

        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }
        if (task == null || task.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "task is required"));
        }
        if (maxChars < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "maxChars must be non-negative"));
        }

        try {
            ICLPromptRequest.Builder builder = ICLPromptRequest.builder()
                    .project(project)
                    .task(task);

            // Only send maxChars when > 0; 0 means "let backend decide" (omit from request)
            if (maxChars > 0) {
                builder.maxChars(maxChars);
            }

            if (userId != null && !userId.isBlank()) {
                builder.userId(userId);
            }

            var result = client.buildICLPrompt(builder.build());
            return ResponseEntity.ok(Map.of(
                    "prompt", result.prompt(),
                    "experienceCount", result.experienceCount(),
                    "maxChars", result.maxChars()));
        } catch (Exception e) {
            log.error("Build ICL prompt failed for project={}", project, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Build ICL prompt failed: " + e.getMessage()));
        }
    }

    /**
     * GET /demo/experiences/health
     */
    @GetMapping(value = "/experiences/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "experiences", "status", "ok"));
    }
}
