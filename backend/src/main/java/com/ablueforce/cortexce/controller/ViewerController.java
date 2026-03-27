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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Viewer", description = "Viewer REST API for the React WebUI — provides all endpoints for browsing observations, summaries, prompts, projects, search, settings, and modes")
public class ViewerController {

    private static final Logger log = LoggerFactory.getLogger(ViewerController.class);

    private final ObservationRepository observationRepository;
    private final SummaryRepository summaryRepository;
    private final UserPromptRepository userPromptRepository;
    private final SessionRepository sessionRepository;
    private final AgentService agentService;
    private final EmbeddingService embeddingService;
    private final SearchService searchService;
    private final ModeService modeService;
    private final SettingsService settingsService;
    private final TimelineService timelineService;

    public ViewerController(ObservationRepository observationRepository,
                            SummaryRepository summaryRepository,
                            UserPromptRepository userPromptRepository,
                            SessionRepository sessionRepository,
                            AgentService agentService,
                            EmbeddingService embeddingService,
                            SearchService searchService,
                            ModeService modeService,
                            SettingsService settingsService,
                            TimelineService timelineService) {
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.userPromptRepository = userPromptRepository;
        this.sessionRepository = sessionRepository;
        this.agentService = agentService;
        this.embeddingService = embeddingService;
        this.searchService = searchService;
        this.modeService = modeService;
        this.settingsService = settingsService;
        this.timelineService = timelineService;
    }

    /**
     * GET /api/observations — paginated observation list.
     * Web UI expects offset/limit parameters and items/hasMore response format.
     */
    @GetMapping("/observations")
    @Operation(summary = "List observations (paginated)",
        description = "Returns a paginated list of observations, optionally filtered by project. Offset and limit are validated against MAX_PAGE_SIZE. Returns items and hasMore for WebUI pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated observation list")
    public ResponseEntity<PagedResponse<ObservationEntity>> getObservations(
        @Parameter(description = "Project path to filter observations (optional, returns all if not specified)", required = false, example = "/Users/dev/my-project")
        @RequestParam(required = false) String project,
        @Parameter(description = "Offset for pagination (0-based)", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Number of items per page (max 100)", required = false, example = "20")
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
    @Operation(summary = "List summaries (paginated)",
        description = "Returns a paginated list of session summaries, optionally filtered by project.")
    @ApiResponse(responseCode = "200", description = "Paginated summary list")
    public ResponseEntity<PagedResponse<SummaryEntity>> getSummaries(
        @Parameter(description = "Project path to filter summaries", required = false, example = "/Users/dev/my-project")
        @RequestParam(required = false) String project,
        @Parameter(description = "Offset for pagination (0-based)", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Number of items per page (max 100)", required = false, example = "20")
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
    @Operation(summary = "List user prompts (paginated)",
        description = "Returns a paginated list of user prompts, optionally filtered by project.")
    @ApiResponse(responseCode = "200", description = "Paginated user prompt list")
    public ResponseEntity<PagedResponse<UserPromptEntity>> getPrompts(
        @Parameter(description = "Project path to filter prompts", required = false, example = "/Users/dev/my-project")
        @RequestParam(required = false) String project,
        @Parameter(description = "Offset for pagination (0-based)", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Number of items per page (max 100)", required = false, example = "20")
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
    @Operation(summary = "List all projects",
        description = "Returns all known project paths that have active or completed sessions.")
    @ApiResponse(responseCode = "200", description = "Project list retrieved")
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
    @Operation(summary = "Get service statistics",
        description = "Returns worker and database statistics including processing status, queue depth, and entity counts.")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved")
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
    @Operation(summary = "Get processing status",
        description = "Returns current processing state and queue depth. Useful for real-time UI updates.")
    @ApiResponse(responseCode = "200", description = "Processing status retrieved")
    public ResponseEntity<Map<String, Object>> getProcessingStatus() {
        return ResponseEntity.ok(Map.of(
            "isProcessing", agentService.isAnySessionProcessing(),
            "queueDepth", agentService.getQueueDepth()
        ));
    }

    /**
     * GET /api/search — semantic search endpoint.
     * <p>
     * Supports offset for filter-only and post-filtered semantic paths.
     * orderBy is accepted for MCP client compatibility but not yet implemented.
     */
    @GetMapping("/search")
    @Operation(summary = "Semantic search observations",
        description = "Performs semantic vector search for observations within a project. Falls back to text-based search if embedding fails. Supports filtering by type, concept, and source. If no query is provided, returns filter-only results.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results returned (with observations list, strategy, and fell_back flag)"),
        @ApiResponse(responseCode = "500", description = "Search failed due to internal error")
    })
    public ResponseEntity<Map<String, Object>> search(
        @Parameter(description = "Project path to search within (required)", required = true, example = "/Users/dev/my-project")
        @RequestParam String project,
        @Parameter(description = "Search query text for semantic search. If empty, returns all observations matching filters.", required = false, example = "how to fix authentication bug")
        @RequestParam(required = false) String query,
        @Parameter(description = "Filter by observation type (e.g., 'bugfix', 'feature', 'architecture')", required = false, example = "bugfix")
        @RequestParam(required = false) String type,
        @Parameter(description = "Filter by observation concept (e.g., 'how-it-works', 'gotcha')", required = false, example = "how-it-works")
        @RequestParam(required = false) String concept,
        @Parameter(description = "Filter by source (e.g., 'manual', 'auto')", required = false, example = "auto")
        @RequestParam(required = false) String source,
        @Parameter(description = "Maximum number of results to return (max 100)", required = false, example = "20")
        @RequestParam(defaultValue = "20") int limit,
        @Parameter(description = "Offset for pagination", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Order by field (accepted for MCP compatibility, not yet fully implemented)", required = false, example = "createdAtEpoch")
        @RequestParam(required = false) String orderBy
    ) {
        // Validate limit against max page size (consistent with other endpoints)
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);

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

        try {
            SearchService.SearchResult result = searchService.search(
                new SearchService.SearchRequest(project, query, queryVector, type, concept, source, null, null, validatedLimit, offset)
            );

            Map<String, Object> response = new HashMap<>();
            response.put("observations", result.observations());
            response.put("strategy", result.strategy());
            response.put("fell_back", result.fellBack());
            response.put("count", result.observations().size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Search failed for project {}: {}", project, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search failed: " + e.getMessage(),
                "observations", List.of(),
                "count", 0
            ));
        }
    }

    /**
     * POST /api/observations/batch — batch get observations by IDs.
     * P0: MCP compatibility - used by get_observations tool.
     */
    @PostMapping("/observations/batch")
    @Operation(summary = "Batch get observations by IDs",
        description = "Retrieves multiple observations by their UUIDs. Supports optional project filtering, ordering by createdAtEpoch, and result limit. Used by MCP compatibility layer.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Observations retrieved"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid ids field")
    })
    public ResponseEntity<Map<String, Object>> batchGetObservations(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body containing ids (list of UUID strings), optional project filter, orderBy field, and limit", required = true)
        @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request
    ) {
        Object idsObj = request.get("ids");
        if (!(idsObj instanceof List<?> idsList) || idsList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing required field: ids",
                "observations", List.of(),
                "count", 0
            ));
        }

        // Validate all elements are strings
        List<String> idStrings = idsList.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();

        if (idStrings.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "ids must be a non-empty list of strings",
                "observations", List.of(),
                "count", 0
            ));
        }

        String project = request.get("project") instanceof String s ? s : null;
        String orderBy = request.get("orderBy") instanceof String s ? s : null;
        Integer limit = request.get("limit") instanceof Number n ? n.intValue() : null;

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

        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No valid UUIDs provided",
                "observations", List.of(),
                "count", 0
            ));
        }

        try {
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
        } catch (Exception e) {
            log.error("Batch get observations failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to get observations: " + e.getMessage(),
                "observations", List.of(),
                "count", 0
            ));
        }
    }

    /**
     * GET /api/settings — settings endpoint.
     * Returns current settings from file with environment variable overrides applied.
     */
    @GetMapping("/settings")
    @Operation(summary = "Get current settings",
        description = "Returns current application settings from file with environment variable overrides applied. Includes mode information.")
    @ApiResponse(responseCode = "200", description = "Settings retrieved successfully")
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
    @Operation(summary = "Get active mode configuration",
        description = "Returns the current active mode configuration including name, description, version, observation types, and observation concepts.")
    @ApiResponse(responseCode = "200", description = "Mode configuration retrieved")
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
    @Operation(summary = "Set active mode",
        description = "Switches the active observation mode at runtime. Mode changes affect which observation types and concepts are considered valid for new observations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mode set successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid mode ID provided")
    })
    public ResponseEntity<Map<String, Object>> setActiveMode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with 'mode' field containing the mode ID to activate", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> request) {
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
    @Operation(summary = "Save settings",
        description = "Persists settings updates to the settings file. If 'mode' or 'CLAUDE_MEM_MODE' is changed, also updates the active ModeService mode.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings saved successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to save settings due to internal error")
    })
    public ResponseEntity<Map<String, Object>> saveSettings(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Settings key-value map to update. Supports 'mode' or 'CLAUDE_MEM_MODE' for mode switching.", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> updates) {
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
            return ResponseEntity.status(500).body(Map.of(
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
    @Operation(summary = "Get observation timeline",
        description = "Returns observations grouped by date for the viewer UI timeline. Supports date range queries and anchor-based queries for MCP compatibility. Date range is limited to 1 year maximum.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Timeline retrieved"),
        @ApiResponse(responseCode = "400", description = "Date range exceeds 1 year maximum or invalid anchor parameters")
    })
    public ResponseEntity<?> getTimeline(
        @Parameter(description = "Project path to query timeline for", required = true, example = "/Users/dev/my-project")
        @RequestParam String project,
        @Parameter(description = "Start timestamp (epoch milliseconds)", required = false, example = "1704067200000")
        @RequestParam(required = false) Long startEpoch,
        @Parameter(description = "End timestamp (epoch milliseconds)", required = false, example = "1706745600000")
        @RequestParam(required = false) Long endEpoch,
        @Parameter(description = "Anchor observation UUID for MCP timeline tool", required = false, example = "550e8400-e29b-41d4-a716-446655440000")
        @RequestParam(required = false) String anchorId,
        @Parameter(description = "Number of items to return before the anchor", required = false, example = "10")
        @RequestParam(required = false) Integer depthBefore,
        @Parameter(description = "Number of items to return after the anchor", required = false, example = "10")
        @RequestParam(required = false) Integer depthAfter,
        @Parameter(description = "Query string to find anchor observation (MCP compatibility)", required = false, example = "authentication bug")
        @RequestParam(required = false) String query
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

        try {
            List<Object[]> results = observationRepository.findTimelineByDate(project, start, end);

            List<Map<String, Object>> timeline = results.stream().map(row -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("date", row[0].toString());
                entry.put("count", ((Number) row[1]).intValue());
                entry.put("ids", row[2]);
                return entry;
            }).toList();

            return ResponseEntity.ok(timeline);
        } catch (Exception e) {
            log.error("Timeline query failed for project {}: {}", project, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Timeline query failed: " + e.getMessage()
            ));
        }
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
    @Operation(summary = "Search observations by file path",
        description = "Finds observations where files_read or files_modified contain a given file or folder path. Used for CLAUDE.md generation and file-level history. When isFolder=true, matches all files under the specified directory prefix.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results returned"),
        @ApiResponse(responseCode = "500", description = "Search failed due to internal error")
    })
    public ResponseEntity<Map<String, Object>> searchByFile(
        @Parameter(description = "Project path to search within", required = true, example = "/Users/dev/my-project")
        @RequestParam String project,
        @Parameter(description = "File or folder path to search for (must be absolute path)", required = true, example = "/Users/dev/my-project/src/auth/login.ts")
        @RequestParam String filePath,
        @Parameter(description = "If true, match folder prefix (e.g., /src/ matches /src/foo.ts)", required = false, example = "false")
        @RequestParam(defaultValue = "false") boolean isFolder,
        @Parameter(description = "Maximum number of results to return (max 100)", required = false, example = "20")
        @RequestParam(defaultValue = "20") int limit,
        @Parameter(description = "Enable debug logging for this request", required = false, example = "false")
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

        try {
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
        } catch (Exception e) {
            log.error("Search by file failed for project={}, filePath={}: {}", project, filePath, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search by file failed: " + e.getMessage(),
                "observations", List.of(),
                "count", 0
            ));
        }
    }

    /**
     * Batch query sessions by content session IDs (Claude Code session ids).
     * Used by export script to get session metadata.
     *
     * POST /api/sdk-sessions/batch
     * { "contentSessionIds": ["id1", "id2", ...] }
     */
    @PostMapping("/sdk-sessions/batch")
    @Operation(summary = "Batch get sessions by content session IDs",
        description = "Retrieves multiple sessions by their Claude Code content session IDs. Used by the export script to get session metadata. Returns session DB ID, content session ID, project, user prompt, timestamps, and status.")
    @ApiResponse(responseCode = "200", description = "Sessions retrieved (empty list if no matching sessions found)")
    public ResponseEntity<List<Map<String, Object>>> batchGetSessions(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with 'contentSessionIds' field containing a list of Claude Code session ID strings", required = true)
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request
    ) {
        Object idsObj = request.get("contentSessionIds");
        if (!(idsObj instanceof List<?> idsList) || idsList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<String> contentSessionIds = idsList.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();

        if (contentSessionIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        try {
            List<SessionEntity> sessions = sessionRepository.findByContentSessionIdIn(contentSessionIds);

            List<Map<String, Object>> result = sessions.stream()
                    .map(s -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", s.getId().toString());
                        map.put("content_session_id", s.getContentSessionId());
                        map.put("project", s.getProjectPath() != null ? s.getProjectPath() : "");
                        map.put("user_prompt", s.getUserPrompt() != null ? s.getUserPrompt() : "");
                        map.put("started_at_epoch", s.getStartedAtEpoch());
                        map.put("completed_at_epoch", s.getCompletedAtEpoch() != null ? s.getCompletedAtEpoch() : 0);
                        map.put("status", s.getStatus() != null ? s.getStatus() : "");
                        return map;
                    })
                    .toList();

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Batch get sessions failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(List.of());
        }
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
