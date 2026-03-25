package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.UserPromptEntity;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.SessionManagementService;
import com.ablueforce.cortexce.service.SummaryGenerationService;
import com.ablueforce.cortexce.service.ContextCacheService;
import com.ablueforce.cortexce.service.RateLimitService;
import com.ablueforce.cortexce.service.SSEBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Ingestion controller — receives hook events from the thin proxy (wrapper.js).
 *
 * This controller handles ONLY events that come from Claude Code hooks.
 * All endpoints are fire-and-forget: they return immediately with 200 OK,
 * and heavy processing is done asynchronously via AgentService.
 *
 * Endpoint mapping (wrapper.js -> this controller):
 *   wrapper.js session-start  -> /api/session/start (SessionController)
 *   wrapper.js tool-use       -> /api/ingest/tool-use
 *   wrapper.js session-end    -> /api/ingest/session-end
 *   wrapper.js user-prompt    -> /api/ingest/user-prompt
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    @Autowired
    private AgentService agentService;

    @Autowired
    private SessionManagementService sessionManagementService;

    @Autowired
    private SummaryGenerationService summaryGenerationService;

    @Autowired
    private UserPromptRepository userPromptRepository;

    @Autowired
    private ContextCacheService contextCacheService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private SSEBroadcaster sseBroadcaster;

    // ==========================================================================
    // Hook Event Handlers (called by wrapper.js)
    // ==========================================================================

    /**
     * PostToolUse hook — enqueue tool use for async observation extraction.
     *
     * Called after: Edit, Write, Read, Bash tools
     * Wrapper: wrapper.js tool-use
     *
     * POST /api/ingest/tool-use
     * {
     *   "session_id": "content-session-id",
     *   "tool_name": "Edit|Write|Read|Bash",
     *   "tool_input": {...},
     *   "tool_response": {...},
     *   "cwd": "/path/to/project"
     * }
     */
    @PostMapping("/tool-use")
    public ResponseEntity<Map<String, String>> handleToolUse(@RequestBody Map<String, Object> body) {
        String contentSessionId = (String) body.get("session_id");
        String toolName = (String) body.get("tool_name");
        
        // Handle both string and object types for tool_input and tool_response
        Object toolInputObj = body.get("tool_input");
        Object toolResponseObj = body.get("tool_response");
        String toolInput = toolInputObj != null ? (toolInputObj instanceof String ? (String) toolInputObj : toolInputObj.toString()) : "{}";
        String toolResponse = toolResponseObj != null ? (toolResponseObj instanceof String ? (String) toolResponseObj : toolResponseObj.toString()) : "{}";
        String cwd = (String) body.get("cwd");

        // P2: Validate required fields
        if (contentSessionId == null || contentSessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: session_id"));
        }
        if (toolName == null || toolName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: tool_name"));
        }

        log.debug("Tool use: session={}, tool={}", contentSessionId, toolName);

        // Rate limit check (10 requests per 60 seconds per session)
        if (!rateLimitService.tryAcquire("tool-use:" + contentSessionId)) {
            log.warn("Rate limit exceeded for session: {}", contentSessionId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Rate limit exceeded", "retry_after", String.valueOf(rateLimitService.getResetSeconds("tool-use:" + contentSessionId))));
        }

        // Resolve session DB ID from content session ID
        java.util.UUID sessionDbId = sessionManagementService.findByContentSessionId(contentSessionId)
            .map(SessionEntity::getId)
            .orElse(null);

        // Fire and forget — async observation extraction
        agentService.processToolUseAsync(
            sessionDbId, contentSessionId,
            toolName, toolInput, toolResponse, cwd, null
        );

        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    /**
     * SessionEnd hook — complete session and trigger async summary generation.
     *
     * Called when: Claude Code session ends
     * Wrapper: wrapper.js session-end
     *
     * POST /api/ingest/session-end
     * {
     *   "session_id": "content-session-id",
     *   "last_assistant_message": "..." (optional, extracted from transcript),
     *   "cwd": "/path/to/project"
     * }
     *
     * @param body Request body containing session_id, last_assistant_message, and debug flag
     * @return Response with status and debug info if debug=true
     */
    @PostMapping("/session-end")
    public ResponseEntity<Map<String, String>> handleSessionEnd(@RequestBody Map<String, Object> body) {
        String contentSessionId = (String) body.get("session_id");
        String lastAssistantMessage = (String) body.get("last_assistant_message");
        Boolean debug = (Boolean) body.get("debug");

        // Validate session_id (consistent with handleUserPrompt)
        if (contentSessionId == null || contentSessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: session_id"));
        }

        log.debug("Session end: session={}", contentSessionId);

        // Fire-and-forget: marks session completed AND generates summary asynchronously
        summaryGenerationService.completeSessionAsync(contentSessionId, lastAssistantMessage);

        // Debug mode: return additional info for testing verification
        if (Boolean.TRUE.equals(debug)) {
            log.info("[DEBUG] SessionEnd received - session_id={}, last_assistant_message length={}",
                contentSessionId, lastAssistantMessage != null ? lastAssistantMessage.length() : 0);
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "debug_session_id", contentSessionId != null ? contentSessionId : "",
                "debug_last_assistant_message", lastAssistantMessage != null ? lastAssistantMessage : ""
            ));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * UserPromptSubmit hook — record user prompt for conversation tracking.
     *
     * Called when: User submits a prompt to Claude Code
     * Wrapper: wrapper.js user-prompt
     *
     * POST /api/ingest/user-prompt
     * {
     *   "session_id": "content-session-id",
     *   "prompt_text": "user's prompt",
     *   "prompt_number": 1,
     *   "cwd": "/path/to/project"
     * }
     */
    @PostMapping("/user-prompt")
    public ResponseEntity<Map<String, String>> handleUserPrompt(@RequestBody Map<String, Object> body) {
        String contentSessionId = (String) body.get("session_id");
        String promptText = (String) body.get("prompt_text");
        String cwd = (String) body.get("cwd");
        Integer promptNumber = 1;
        Object promptNumObj = body.get("prompt_number");
        if (promptNumObj instanceof Number n) {
            promptNumber = n.intValue();
        }

        // P1: Validate session_id is provided
        if (contentSessionId == null || contentSessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: session_id"));
        }

        // P1: Sanitize promptText to prevent injection
        if (promptText == null) {
            promptText = "";
        }
        // Limit length and sanitize
        if (promptText.length() > Constants.MAX_USER_PROMPT_LENGTH) {
            log.warn("Prompt text exceeded max length {}, truncating", Constants.MAX_USER_PROMPT_LENGTH);
            promptText = promptText.substring(0, Constants.MAX_USER_PROMPT_LENGTH);
        }

        log.debug("User prompt: session={}, prompt_number={}", contentSessionId, promptNumber);

        // P0: Ensure session exists before inserting user prompt (fixes FK constraint error)
        // This handles the case where SessionStart hook failed or was skipped,
        // but UserPromptSubmit still needs to record the prompt.
        // If session already exists, this is a no-op; otherwise creates a new session.
        sessionManagementService.ensureSession(contentSessionId, cwd, promptText);

        UserPromptEntity prompt = new UserPromptEntity();
        prompt.setContentSessionId(contentSessionId);
        prompt.setPromptText(promptText);
        prompt.setPromptNumber(promptNumber);
        prompt.setProjectPath(cwd);  // Set project path from cwd
        prompt.setCreatedAtEpoch(Instant.now().toEpochMilli());
        UserPromptEntity saved = userPromptRepository.save(prompt);

        // Broadcast SSE event for new_prompt
        // TypeScript useSSE.ts expects "prompt" key
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("type", "new_prompt");
        eventData.put("prompt", saved);
        sseBroadcaster.broadcast(eventData, "new_prompt");

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ==========================================================================
    // Internal/Test Endpoints (not exposed to wrapper.js)
    // ==========================================================================

    /**
     * Create an observation directly with auto-embedding.
     * FOR TESTING ONLY - not called by wrapper.js
     *
     * POST /api/ingest/observation
     * {
     *   "content_session_id": "...",
     *   "project_path": "/path/to/project",
     *   ...
     * }
     * Also accepts {@code session_id} as an alias for {@code content_session_id}.
     */
    @PostMapping("/observation")
    public ResponseEntity<?> handleObservation(@RequestBody Map<String, Object> body) {
        String contentSessionId = safeGetString(body, "content_session_id");
        if (contentSessionId == null || contentSessionId.isBlank()) {
            contentSessionId = safeGetString(body, "session_id");
        }
        String projectPath = safeGetString(body, "project_path");
        if (contentSessionId == null || contentSessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing required field: content_session_id (or session_id)"));
        }
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: project_path"));
        }

        // Create ParsedObservation with public fields (not setters)
        // P1: Use type-safe extraction to prevent ClassCastException
        com.ablueforce.cortexce.util.XmlParser.ParsedObservation parsed =
            new com.ablueforce.cortexce.util.XmlParser.ParsedObservation();
        parsed.type = safeGetString(body, "type", "change");
        parsed.title = safeGetString(body, "title");
        parsed.subtitle = safeGetString(body, "subtitle");
        parsed.narrative = safeGetString(body, "narrative");
        // Fallback: accept "content" as alias for "narrative" (SDK compatibility)
        if (parsed.narrative == null) {
            parsed.narrative = safeGetString(body, "content");
        }
        parsed.facts = safeGetStringList(body, "facts");
        parsed.concepts = safeGetStringList(body, "concepts");
        parsed.filesRead = safeGetStringList(body, "files_read");
        parsed.filesModified = safeGetStringList(body, "files_modified");
        // V14: source and extracted data
        parsed.source = safeGetString(body, "source");
        parsed.extractedData = safeGetMap(body, "extractedData");

        // P0: Ensure session exists before creating observation (fixes FK constraint error)
        // This handles the case where SessionStart hook failed or was skipped.
        // If session already exists, this is a no-op; otherwise creates a new session.
        sessionManagementService.ensureSession(contentSessionId, projectPath, parsed.title);

        var observation = agentService.saveObservation(
            contentSessionId,
            projectPath,
            parsed,
            safeGetInt(body, "prompt_number"),
            0 // discoveryTokens - 0 for direct import (no LLM call)
        );

        // Mark context for refresh since new observation was added
        if (projectPath != null) {
            contextCacheService.markForRefresh(projectPath);
        }

        return ResponseEntity.ok(observation);
    }

    /**
     * P1: Safely extract a String from a Map with type checking.
     *
     * @param body the request body map
     * @param key the key to extract
     * @return the string value or null if not present or wrong type
     */
    private String safeGetString(Map<String, Object> body, String key) {
        return safeGetString(body, key, null);
    }

    /**
     * P1: Safely extract a String from a Map with type checking and default value.
     *
     * @param body the request body map
     * @param key the key to extract
     * @param defaultValue the default value if key not found or wrong type
     * @return the string value or default
     */
    private String safeGetString(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String) {
            return (String) value;
        }
        log.warn("Expected String for key '{}' but got {}", key, value.getClass().getName());
        return defaultValue;
    }

    /**
     * P1: Safely extract an Integer from a Map with type checking.
     *
     * @param body the request body map
     * @param key the key to extract
     * @return the integer value or null if not present or wrong type
     */
    private Integer safeGetInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        log.warn("Expected Number for key '{}' but got {}", key, value.getClass().getName());
        return null;
    }

    /**
     * P1: Safely extract a List of Strings from a Map with type checking.
     *
     * @param body the request body map
     * @param key the key to extract
     * @return the list value or empty list if not present or wrong type
     */
    private java.util.List<String> safeGetStringList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return java.util.List.of();
        }
        if (value instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) value;
            java.util.List<String> result = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    log.warn("Expected String in list for key '{}' but got {}", key, item.getClass().getName());
                }
            }
            return result;
        }
        log.warn("Expected List for key '{}' but got {}", key, value.getClass().getName());
        return java.util.List.of();
    }

    /**
     * V14: Safely extract a Map from a Map with type checking.
     * Used for extractedData field.
     *
     * @param body the request body map
     * @param key the key to extract
     * @return the map value or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> safeGetMap(java.util.Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.Map) {
            return (java.util.Map<String, Object>) value;
        }
        log.warn("Expected Map for key '{}' but got {}", key, value.getClass().getName());
        return null;
    }
}
