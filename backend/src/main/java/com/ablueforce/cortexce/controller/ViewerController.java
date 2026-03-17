package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.config.Constants;
import com.ablueforce.cortexce.config.AppSettings;
import com.ablueforce.cortexce.config.ModeConfig.Mode;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.entity.UserPromptEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import com.ablueforce.cortexce.service.AgentService;
import com.ablueforce.cortexce.service.ModeService;
import com.ablueforce.cortexce.service.SettingsService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.SearchService;
import com.ablueforce.cortexce.service.TimelineService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Viewer REST API controller.
 * <p>
 * Provides all endpoints needed by the React Viewer UI (1:1 with original TS backend).
 */
@RestController
@RequestMapping("/api")
public class ViewerController {

    private static final Logger log = LoggerFactory.getLogger(ViewerController.class);

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private UserPromptRepository userPromptRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private AgentService agentService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ModeService modeService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private TimelineService timelineService;

    /**
     * GET /api/observations — paginated observation list.
     * Web UI expects offset/limit parameters and items/hasMore response format.
     */
    @GetMapping("/observations")
    public ResponseEntity<PagedResponse<ObservationEntity>> getObservations(
        @RequestParam(required = false) String project,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "20") int limit
    ) {
        // Validate pagination parameters
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        Page<ObservationEntity> result = observationRepository.findAllPaged(project, PageRequest.of(validatedOffset / validatedLimit, validatedLimit));
        return ResponseEntity.ok(PagedResponse.of(result));
    }

    /**
     * GET /api/summaries — paginated summary list.
     * Web UI expects offset/limit parameters and items/hasMore response format.
     */
    @GetMapping("/summaries")
    public ResponseEntity<PagedResponse<SummaryEntity>> getSummaries(
        @RequestParam(required = false) String project,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "20") int limit
    ) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        Page<SummaryEntity> result = summaryRepository.findAllPaged(project, PageRequest.of(validatedOffset / validatedLimit, validatedLimit));
        return ResponseEntity.ok(PagedResponse.of(result));
    }

    /**
     * GET /api/prompts — paginated user prompt list.
     * Web UI expects offset/limit parameters and items/hasMore response format.
     */
    @GetMapping("/prompts")
    public ResponseEntity<PagedResponse<UserPromptEntity>> getPrompts(
        @RequestParam(required = false) String project,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "20") int limit
    ) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        Page<UserPromptEntity> result = userPromptRepository.findAllPaged(project, PageRequest.of(validatedOffset / validatedLimit, validatedLimit));
        return ResponseEntity.ok(PagedResponse.of(result));
    }

    /**
     * GET /api/projects — list all known projects.
     * Web UI expects {projects: [...]} format.
     */
    @GetMapping("/projects")
    public ResponseEntity<Map<String, Object>> getProjects() {
        return ResponseEntity.ok(Map.of("projects", sessionRepository.findAllProjects()));
    }

    /**
     * GET /api/concepts — list all unique concepts from observations.
     * Used by WebUI to populate the concepts filter dropdown.
     * Returns concepts sorted alphabetically.
     *
     * NOTE: Temporarily disabled - using fixed concepts from TS mode configuration
     * to ensure consistency between data generation and filtering.
     */
//    @GetMapping("/concepts")
//    public ResponseEntity<Map<String, Object>> getConcepts() {
//        List<String> concepts = observationRepository.findAllConcepts();
//        return ResponseEntity.ok(Map.of("concepts", concepts));
//    }

    /**
     * GET /api/stats — worker/database statistics.
     * Web UI expects nested {worker: {...}, database: {...}} structure.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> worker = Map.of(
            "isProcessing", agentService.isAnySessionProcessing(),
            "queueDepth", agentService.getQueueDepth()
        );
        Map<String, Object> database = Map.of(
            "totalObservations", observationRepository.count(),
            "totalSummaries", summaryRepository.count(),
            "totalSessions", sessionRepository.count(),
            "totalProjects", sessionRepository.countDistinctProjects()
        );
        return ResponseEntity.ok(Map.of("worker", worker, "database", database));
    }

    /**
     * GET /api/processing-status — current processing state and queue depth.
     * Web UI expects camelCase field names.
     */
    @GetMapping("/processing-status")
    public ResponseEntity<Map<String, Object>> getProcessingStatus() {
        return ResponseEntity.ok(Map.of(
            "isProcessing", agentService.isAnySessionProcessing(),
            "queueDepth", agentService.getQueueDepth()
        ));
    }

    /**
     * GET /api/search — semantic search endpoint.
     * P0: Added offset and orderBy parameters for MCP compatibility.
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
        @RequestParam String project,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String concept,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(required = false) String orderBy
    ) {
        float[] queryVector = null;
        if (query != null && !query.isBlank()) {
            try {
                queryVector = embeddingService.embed(query);
            } catch (Exception e) {
                // P0: Log embedding failure at WARN level for debugging
                log.warn("Embedding failed for query: {}, falling back to text search: {}",
                    query.substring(0, Math.min(50, query.length())), e.getMessage());
            }
        }

        SearchService.SearchResult result = searchService.search(
            new SearchService.SearchRequest(project, query, queryVector, type, concept, null, null, limit)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("observations", result.observations());
        response.put("strategy", result.strategy());
        response.put("fell_back", result.fellBack());
        response.put("count", result.observations().size());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/observations/batch — batch get observations by IDs.
     * P0: MCP compatibility - used by get_observations tool.
     */
    @PostMapping("/observations/batch")
    public ResponseEntity<Map<String, Object>> batchGetObservations(
        @RequestBody Map<String, Object> request
    ) {
        @SuppressWarnings("unchecked")
        List<String> idStrings = (List<String>) request.get("ids");
        String project = (String) request.get("project");
        String orderBy = (String) request.get("orderBy");
        Integer limit = request.get("limit") != null
            ? ((Number) request.get("limit")).intValue() : null;

        if (idStrings == null || idStrings.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing required field: ids",
                "observations", List.of(),
                "count", 0
            ));
        }

        // Convert string IDs to UUIDs
        List<java.util.UUID> ids = idStrings.stream()
            .map(id -> {
                try {
                    return java.util.UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID format: {}", id);
                    return null;
                }
            })
            .filter(id -> id != null)
            .toList();

        List<ObservationEntity> observations = observationRepository.findAllById(ids);

        // Apply project filter if specified
        if (project != null && !project.isBlank()) {
            observations = observations.stream()
                .filter(o -> project.equals(o.getProjectPath()))
                .toList();
        }

        // Apply ordering if specified
        if ("created_at_epoch".equals(orderBy) || "createdAtEpoch".equals(orderBy)) {
            observations = observations.stream()
                .sorted((a, b) -> Long.compare(
                    b.getCreatedAtEpoch() != null ? b.getCreatedAtEpoch() : 0,
                    a.getCreatedAtEpoch() != null ? a.getCreatedAtEpoch() : 0
                ))
                .toList();
        }

        // Apply limit if specified
        if (limit != null && limit > 0 && observations.size() > limit) {
            observations = observations.subList(0, limit);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("observations", observations);
        response.put("count", observations.size());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/settings — settings endpoint.
     * Returns current settings from file with environment variable overrides applied.
     */
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        AppSettings appSettings = settingsService.getSettings();
        Map<String, Object> response = appSettings.toMap();

        // Add mode information from ModeService
        Mode activeMode = modeService.getActiveMode();
        response.put("modeName", activeMode.name());
        response.put("modeDescription", activeMode.description());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/modes — get active mode configuration.
     */
    @GetMapping("/modes")
    public ResponseEntity<Map<String, Object>> getActiveMode() {
        Mode mode = modeService.getActiveMode();
        Map<String, Object> response = new HashMap<>();
        response.put("id", modeService.getConfiguredMode());
        response.put("name", mode.name());
        response.put("description", mode.description());
        response.put("version", mode.version());
        response.put("observationTypes", mode.observation_types());
        response.put("observationConcepts", mode.observation_concepts());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/modes — set active mode.
     */
    @PostMapping("/modes")
    public ResponseEntity<Map<String, Object>> setActiveMode(@RequestBody Map<String, String> request) {
        String modeId = request.get("mode");
        if (modeId == null || modeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Mode ID is required"
            ));
        }

        try {
            modeService.setActiveMode(modeId);
            Mode mode = modeService.getActiveMode();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "mode", modeId,
                "name", mode.name()
            ));
        } catch (Exception e) {
            log.warn("Failed to set mode '{}': {}", modeId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to load mode: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /api/settings — save settings endpoint.
     * Persists settings to file and returns updated settings.
     * WebUI expects: {success: boolean, error?: string}
     */
    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> saveSettings(@RequestBody Map<String, Object> updates) {
        try {
            settingsService.updateSettings(updates);

            // If mode changed, update ModeService
            if (updates.containsKey("mode") || updates.containsKey("CLAUDE_MEM_MODE")) {
                String newMode = updates.containsKey("mode")
                    ? String.valueOf(updates.get("mode"))
                    : String.valueOf(updates.get("CLAUDE_MEM_MODE"));
                modeService.setActiveMode(newMode);
            }

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Failed to save settings: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/timeline — observation timeline grouped by date.
     * P2: View observations grouped by date for the viewer UI.
     * P2 FIX: Added date range validation to prevent expensive queries on large ranges.
     * P0: Added anchor query support for MCP compatibility.
     */
    @GetMapping("/timeline")
    public ResponseEntity<?> getTimeline(
        @RequestParam String project,
        @RequestParam(required = false) Long startEpoch,
        @RequestParam(required = false) Long endEpoch,
        @RequestParam(required = false) String anchorId,      // P0: Anchor observation ID
        @RequestParam(required = false) Integer depthBefore,  // P0: Items before anchor
        @RequestParam(required = false) Integer depthAfter,   // P0: Items after anchor
        @RequestParam(required = false) String query          // P0: Query to find anchor
    ) {
        // P0: Anchor-based query mode (for MCP timeline tool)
        if (anchorId != null || query != null) {
            return timelineService.getTimelineByAnchor(project, anchorId, query, depthBefore, depthAfter);
        }

        // Default to last 90 days if not specified
        long start = startEpoch != null ? startEpoch
            : Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli();
        long end = endEpoch != null ? endEpoch
            : Instant.now().toEpochMilli();

        // P2: Validate date range to prevent expensive queries (max 1 year)
        long maxRangeMs = 365L * 24 * 60 * 60 * 1000;
        if (end - start > maxRangeMs) {
            log.warn("Timeline date range exceeds 1 year: {} days", (end - start) / (24 * 60 * 60 * 1000));
            return ResponseEntity.badRequest().body(Map.of("error", "Date range exceeds 1 year maximum"));
        }

        List<Object[]> results = observationRepository.findTimelineByDate(project, start, end);

        List<Map<String, Object>> timeline = results.stream().map(row -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("date", row[0].toString());
            entry.put("count", ((Number) row[1]).intValue());
            entry.put("ids", row[2]);
            return entry;
        }).toList();

        return ResponseEntity.ok(timeline);
    }

    /**
     * GET /api/search/by-file — find observations related to a file or folder path.
     * TS Alignment: Used for folder-level CLAUDE.md generation.
     * Matches observations where files_read or files_modified contain the given path.
     *
     * @param project Project path
     * @param filePath File or folder path to search for
     * @param isFolder If true, match folder prefix (e.g., /src/ matches /src/foo.ts)
     * @param limit Maximum results (default 20)
     */
    @GetMapping("/search/by-file")
    public ResponseEntity<Map<String, Object>> searchByFile(
        @RequestParam String project,
        @RequestParam String filePath,
        @RequestParam(defaultValue = "false") boolean isFolder,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "false") boolean debug
    ) {
        if (debug) {
            log.info("[DEBUG] searchByFile called with project='{}', filePath='{}', isFolder={}, limit={}", 
                project, filePath, isFolder, limit);
        }
        
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);

        // Normalize path for matching
        String normalizedPath = filePath;
        if (isFolder && !normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }

        if (debug) {
            log.info("[DEBUG] Searching with normalizedPath='{}', folderPath will be='{}%'", normalizedPath, normalizedPath);
        }
        
        List<ObservationEntity> observations = observationRepository.findByFolderPath(
            project, normalizedPath, validatedLimit
        );

        if (debug) {
            log.info("[DEBUG] Found {} observations", observations.size());
            for (ObservationEntity obs : observations) {
                log.info("[DEBUG] Observation: id={}, files_modified={}", obs.getId(), obs.getFilesModified());
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("observations", observations);
        response.put("count", observations.size());
        response.put("filePath", filePath);
        response.put("isFolder", isFolder);
        return ResponseEntity.ok(response);
    }

    /**
     * Batch query sessions by memory session IDs.
     * Used by export script to get session metadata.
     *
     * POST /api/sdk-sessions/batch
     * { "memorySessionIds": ["id1", "id2", ...] }
     */
    @PostMapping("/sdk-sessions/batch")
    public ResponseEntity<List<Map<String, Object>>> batchGetSessions(
            @RequestBody Map<String, Object> request
    ) {
        @SuppressWarnings("unchecked")
        List<String> memorySessionIds = (List<String>) request.get("memorySessionIds");

        if (memorySessionIds == null || memorySessionIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<SessionEntity> sessions = sessionRepository.findByMemorySessionIdIn(memorySessionIds);

        List<Map<String, Object>> result = sessions.stream()
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", s.getId().toString());
                    map.put("content_session_id", s.getContentSessionId());
                    map.put("memory_session_id", s.getMemorySessionId() != null ? s.getMemorySessionId() : "");
                    map.put("project", s.getProjectPath() != null ? s.getProjectPath() : "");
                    map.put("user_prompt", s.getUserPrompt() != null ? s.getUserPrompt() : "");
                    map.put("started_at_epoch", s.getStartedAtEpoch());
                    map.put("completed_at_epoch", s.getCompletedAtEpoch() != null ? s.getCompletedAtEpoch() : 0);
                    map.put("status", s.getStatus() != null ? s.getStatus() : "");
                    return map;
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Generic paged response wrapper matching Web UI expectations.
     * Web UI expects: {items: [...], hasMore: boolean}
     */
    public record PagedResponse<T>(
        @JsonProperty("items") List<T> items,
        @JsonProperty("hasMore") boolean hasMore
    ) {
        public static <T> PagedResponse<T> of(Page<T> page) {
            return new PagedResponse<>(
                page.getContent(),
                page.hasNext()
            );
        }
    }
}
