package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Memory Controller - handles manual memory/observation saving.
 *
 * POST /api/memory/save - Save a manual memory observation
 * Mirrors TypeScript implementation in MemoryRoutes.ts
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * POST /api/memory/save - Save a manual memory/observation
     *
     * Body: { text: string, title?: string, project?: string }
     *
     * Returns: { success: boolean, id?: string, title?: string, project?: string, message?: string, error?: string }
     */
    @PostMapping(value = "/save", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> saveMemory(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String title = body.get("title");
        String project = body.get("project");

        // Default project if not specified
        String targetProject = (project != null && !project.isBlank()) ? project : "manual-memories";

        log.info("Memory save request: title={}, project={}", title, targetProject);

        Map<String, Object> response = new HashMap<>();

        // Validate text
        if (text == null || text.isBlank()) {
            response.put("success", false);
            response.put("error", "text is required and must be non-empty");
            return response;
        }

        try {
            // Generate unique session ID for manual memory
            String memorySessionId = "manual-" + System.currentTimeMillis();

            // Create dummy session first to satisfy FK constraint
            SessionEntity dummySession = new SessionEntity();
            dummySession.setContentSessionId(memorySessionId);
            dummySession.setMemorySessionId(memorySessionId);
            dummySession.setProjectPath(targetProject);
            dummySession.setStartedAtEpoch(System.currentTimeMillis());
            dummySession.setStatus("completed");
            sessionRepository.save(dummySession);

            // Generate title if not provided (TS: substring 0-60)
            String effectiveTitle = title;
            if (effectiveTitle == null || effectiveTitle.isBlank()) {
                effectiveTitle = text.length() > 60
                    ? text.substring(0, 60).trim() + "..."
                    : text.trim();
            }

            // Create observation entity
            // TS uses type='discovery' and subtitle='Manual memory'
            ObservationEntity observation = new ObservationEntity();
            observation.setContent(text);
            observation.setTitle(effectiveTitle);
            observation.setSubtitle("Manual memory");
            observation.setProjectPath(targetProject);
            observation.setType("discovery");  // TS uses 'discovery' type
            observation.setMemorySessionId(memorySessionId);
            observation.setCreatedAtEpoch(System.currentTimeMillis());
            observation.setPromptNumber(0);
            observation.setDiscoveryTokens(0);

            // Generate embedding
            float[] embedding = embeddingService.embed(text);
            observation.setEmbedding1024(embedding);
            observation.setEmbeddingModelId("bge-m3");

            // Save observation
            ObservationEntity saved = observationRepository.save(observation);

            log.info("Manual observation saved: id={}, project={}, title={}",
                saved.getId(), targetProject, effectiveTitle);

            response.put("success", true);
            response.put("id", saved.getId().toString());
            response.put("title", effectiveTitle);
            response.put("project", targetProject);
            response.put("message", "Memory saved as observation #" + saved.getId());

        } catch (Exception e) {
            log.error("Failed to save memory: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Failed to save memory: " + e.getMessage());
        }

        return response;
    }
}
