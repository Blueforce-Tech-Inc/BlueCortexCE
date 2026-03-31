package com.example.cortexmem;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.ai.observation.ObservationCaptureService;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo controller for Ingest APIs (prompt + session-end).
 * Demonstrates direct ingestion of user prompts and session lifecycle events.
 *
 * <p>Cross-SDK parity: Go /ingest/prompt + /ingest/session-end,
 * Python /ingest/prompt + /ingest/session-end, JS /ingest/prompt + /ingest/session-end.
 */
@RestController
@RequestMapping("/demo/ingest")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final ObservationCaptureService captureService;

    public IngestController(ObservationCaptureService captureService) {
        this.captureService = captureService;
    }

    /**
     * POST /demo/ingest/prompt
     * Body: {"project": "/path", "session_id": "...", "prompt": "...", "prompt_number": 1}
     */
    @PostMapping(value = "/prompt", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingestPrompt(
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "request body is required"));
        }

        Object projectObj = body.get("project");
        if (!(projectObj instanceof String project) || project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }

        Object sessionIdObj = body.get("session_id");
        if (!(sessionIdObj instanceof String sessionId) || sessionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "session_id is required"));
        }

        Object promptObj = body.get("prompt");
        if (!(promptObj instanceof String prompt) || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "prompt is required"));
        }

        int promptNumber = 0;
        if (body.containsKey("prompt_number")) {
            Object pnObj = body.get("prompt_number");
            if (pnObj instanceof Number n) {
                promptNumber = n.intValue();
            }
        }

        try {
            CortexSessionContext.begin(sessionId, project);
            captureService.recordUserPrompt(UserPromptRequest.builder()
                    .sessionId(sessionId)
                    .projectPath(project)
                    .promptText(prompt)
                    .promptNumber(promptNumber)
                    .build());
            return ResponseEntity.ok(Map.of("status", "recorded"));
        } catch (Exception e) {
            log.error("Ingest prompt failed for sessionId={}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ingest prompt failed: " + e.getMessage()));
        } finally {
            CortexSessionContext.end();
        }
    }

    /**
     * POST /demo/ingest/session-end
     * Body: {"project": "/path", "session_id": "...", "last_assistant_message": "..."}
     */
    @PostMapping(value = "/session-end", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingestSessionEnd(
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "request body is required"));
        }

        Object projectObj = body.get("project");
        if (!(projectObj instanceof String project) || project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }

        Object sessionIdObj = body.get("session_id");
        if (!(sessionIdObj instanceof String sessionId) || sessionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "session_id is required"));
        }

        String lastMessage = "";
        if (body.containsKey("last_assistant_message")) {
            Object msgObj = body.get("last_assistant_message");
            if (msgObj instanceof String s) {
                lastMessage = s;
            }
        }

        try {
            captureService.recordSessionEnd(SessionEndRequest.builder()
                    .sessionId(sessionId)
                    .projectPath(project)
                    .lastAssistantMessage(lastMessage)
                    .build());
            return ResponseEntity.ok(Map.of("status", "ended"));
        } catch (Exception e) {
            log.error("Ingest session-end failed for sessionId={}", sessionId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Ingest session-end failed: " + e.getMessage()));
        }
    }
}
