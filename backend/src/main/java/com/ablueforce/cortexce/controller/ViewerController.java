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
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import com.ablueforce.cortexce.dto.OffsetPageRequest;
import org.springframework.web.bind.annotation.*;

import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.SearchService;
import com.ablueforce.cortexce.service.TimelineService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        @Parameter(description = "Platform source filter (optional)", required = false, example = "claude")
        @RequestParam(required = false) String platformSource,
        @Parameter(description = "Offset for pagination (0-based)", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Number of items per page (max 100)", required = false, example = "20")
        @RequestParam(defaultValue = "20") int limit
    ) {
        // Validate pagination parameters
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        Page<ObservationEntity> result = observationRepository.findAllPaged(project, platformSource,
            new OffsetPageRequest(0, validatedLimit, validatedOffset,
                Sort.by(Sort.Direction.DESC, "createdAt")));
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
        @Parameter(description = "Platform source filter (optional)", required = false, example = "claude")
        @RequestParam(required = false) String platformSource,
        @Parameter(description = "Offset for pagination (0-based)", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Number of items per page (max 100)", required = false, example = "20")
        @RequestParam(defaultValue = "20") int limit
    ) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        Page<SummaryEntity> result = summaryRepository.findAllPaged(project, platformSource,
            new OffsetPageRequest(0, validatedLimit, validatedOffset,
                Sort.by(Sort.Direction.DESC, "createdAt")));
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
        @Parameter(description = "Platform source filter (optional)", required = false, example = "claude")
        @RequestParam(required = false) String platformSource,
        @Parameter(description = "Offset for pagination (0-based)", required = false, example = "0")
        @RequestParam(defaultValue = "0") int offset,
        @Parameter(description = "Number of items per page (max 100)", required = false, example = "20")
        @RequestParam(defaultValue = "20") int limit
    ) {
        int validatedLimit = Math.min(Math.max(1, limit), Constants.MAX_PAGE_SIZE);
        int validatedOffset = Math.max(0, offset);
        Page<UserPromptEntity> result = userPromptRepository.findAllPaged(project, platformSource,
            new OffsetPageRequest(0, validatedLimit, validatedOffset,
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(PagedResponse.of(result));
    }

    /**
     * GET /api/projects — list all known projects with source grouping.
     * V18: Returns sources and projectsBySource for platform filtering.
     */
    @GetMapping("/projects")
    @Operation(summary = "List all projects",
        description = "Returns all known project paths that have active or completed sessions, plus platform sources and grouping.")
    @ApiResponse(responseCode = "200", description = "Project list retrieved with sources",
        content = @Content(schema = @Schema(example = "{\"projects\":[\"/Users/dev/project1\"],\"sources\":[\"claude\"],\"projectsBySource\":{\"claude\":[\"/Users/dev/project1\"]}}")))
    public ResponseEntity<Map<String, Object>> getProjects() {
        List<String> projects = sessionRepository.findAllProjects();
        List<String> sources = sessionRepository.findAllPlatformSources();

        List<Object[]> projectsBySourceRaw = sessionRepository.findProjectsByPlatformSource();
        Map<String, List<String>> projectsBySource = new HashMap<>();
        for (Object[] row : projectsBySourceRaw) {
            String source = (String) row[0];
            String proj = (String) row[1];
            projectsBySource.computeIfAbsent(source, k -> new ArrayList<>()).add(proj);
        }

        return ResponseEntity.ok(Map.of(
            "projects", projects,
            "sources", sources,
            "projectsBySource", projectsBySource
        ));
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
     * Optional project filter: if project query param is provided, returns project-scoped stats.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get service statistics",
        description = "Returns worker and database statistics including processing status, queue depth, and entity counts. " +
            "When project query param is provided, returns project-scoped counts.")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved",
        content = @Content(schema = @Schema(example = "{\"worker\":{\"isProcessing\":false,\"queueDepth\":0},\"database\":{\"totalObservations\":100,\"totalSummaries\":10,\"totalSessions\":20,\"totalProjects\":3}}")))
    public ResponseEntity<Map<String, Object>> getStats(
            @Parameter(description = "Optional project path to filter statistics") @RequestParam(required = false) String project) {
        Map<String, Object> worker = Map.of(
            "isProcessing", agentService.isAnySessionProcessing(),
            "queueDepth", agentService.getQueueDepth()
        );

        Map<String, Object> database;
        if (project != null && !project.isBlank()) {
            // Project-scoped stats (addresses B11-1: SDK projectPath param now respected)
            database = Map.of(
                "totalObservations", observationRepository.countByProjectPath(project),
                "totalSummaries", summaryRepository.countByProjectPath(project),
                "totalSessions", sessionRepository.countByProjectPath(project),
                "totalProjects", 1L,
                "projectPath", project
            );
        } else {
            // Global stats
            database = Map.of(
                "totalObservations", observationRepository.count(),
                "totalSummaries", summaryRepository.count(),
                "totalSessions", sessionRepository.count(),
                "totalProjects", sessionRepository.countDistinctProjects()
            );
        }
        return ResponseEntity.ok(Map.of("worker", worker, "database", database));
    }

    /**
     * GET /api/processing-status — current processing state and queue depth.
     * Web UI expects camelCase field names.
     */
    @GetMapping("/processing-status")
    @Operation(summary = "Get processing status",
        description = "Returns current processing state and queue depth. Useful for real-time UI updates.")
    @ApiResponse(responseCode = "200", description = "Processing status retrieved",
        content = @Content(schema = @Schema(example = "{\"isProcessing\":false,\"queueDepth\":0}")))
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
     * orderBy is accepted for MCP client compatibility (supports: `created_at_epoch`).
     */
    @GetMapping("/search")
    @Operation(summary = "Semantic search observations",
        description = "Performs semantic vector search for observations within a project. Falls back to text-based search if embedding fails. Supports filtering by type, concept, and source. If no query is provided, returns filter-only results.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Search results returned (with observations list, strategy, and fell_back flag)",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.SearchResponse.class))),
        @ApiResponse(responseCode = "500", description = "Search failed due to internal error",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class)))
    })
    public ResponseEntity<com.ablueforce.cortexce.dto.ApiResponses.SearchResponse> search(
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
        @Parameter(description = "Order by field (currently only 'created_at_epoch' / 'createdAtEpoch' is supported — orders by observation creation time descending)", required = false, example = "createdAtEpoch")
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
                new SearchService.SearchRequest(project, query, queryVector, type, concept, source, null, null, validatedLimit, offset, orderBy)
            );

            return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.SearchResponse(
                result.observations(),
                result.strategy(),
                result.fellBack(),
                result.observations().size()
            ));
        } catch (Exception e) {
            log.error("Search failed for project {}: {}", project, e.getMessage());
            return ResponseEntity.status(500).body(new com.ablueforce.cortexce.dto.ApiResponses.SearchResponse(
                List.of(),
                null,
                false,
                0
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
        @ApiResponse(responseCode = "200", description = "Observations retrieved",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid ids field",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class)))
    })
    public ResponseEntity<com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse> batchGetObservations(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Batch observation retrieval request",
            required = true,
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiRequests.BatchObservationsRequest.class)))
        @org.springframework.web.bind.annotation.RequestBody com.ablueforce.cortexce.dto.ApiRequests.BatchObservationsRequest request
    ) {
        List<String> idStrings = request.ids();
        if (idStrings == null || idStrings.isEmpty()) {
            return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse(
                List.of(), 0
            ));
        }

        // Filter to only string elements (defensive)
        idStrings = idStrings.stream()
            .filter(s -> s != null && !s.isBlank())
            .toList();

        if (idStrings.isEmpty()) {
            return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse(
                List.of(), 0
            ));
        }

        String project = request.project();
        String orderBy = request.orderBy();
        Integer limit = request.limit();

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
            return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse(
                List.of(), 0
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

            return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse(
                observations, observations.size()
            ));
        } catch (Exception e) {
            log.error("Batch get observations failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(new com.ablueforce.cortexce.dto.ApiResponses.BatchGetObservationsResponse(
                List.of(), 0
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
    @ApiResponse(responseCode = "200", description = "Settings retrieved successfully",
        content = @Content(schema = @Schema(example = "{\"modeName\":\"code\",\"modeDescription\":\"Tracks code evolution\"}")))
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
    @ApiResponse(responseCode = "200", description = "Mode configuration retrieved",
        content = @Content(schema = @Schema(example = "{\"id\":\"code\",\"name\":\"Code\",\"description\":\"Tracks code evolution\",\"version\":\"1.0\",\"observation_types\":[],\"observation_concepts\":[]}")))
    public ResponseEntity<Map<String, Object>> getActiveMode() {
        Mode mode = modeService.getActiveMode();
        Map<String, Object> response = new HashMap<>();
        response.put("id", modeService.getConfiguredMode());
        response.put("name", mode.name());
        response.put("description", mode.description());
        response.put("version", mode.version());
        response.put("observation_types", mode.observation_types());
        response.put("observation_concepts", mode.observation_concepts());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/modes — set active mode.
     */
    @PostMapping("/modes")
    @Operation(summary = "Set active mode",
        description = "Switches the active observation mode at runtime. Mode changes affect which observation types and concepts are considered valid for new observations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mode set successfully",
            content = @Content(schema = @Schema(example = "{\"success\":true,\"mode\":\"code\",\"name\":\"Code\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid mode ID provided",
            content = @Content(schema = @Schema(example = "{\"success\":false,\"error\":\"Failed to load mode: ...\"}")))
    })
    public ResponseEntity<Map<String, Object>> setActiveMode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Mode switch request",
                required = true,
                content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiRequests.ModeSwitchRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody com.ablueforce.cortexce.dto.ApiRequests.ModeSwitchRequest request) {
        String modeId = request.mode();
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
        @ApiResponse(responseCode = "200", description = "Settings saved successfully",
            content = @Content(schema = @Schema(example = "{\"success\":true}"))),
        @ApiResponse(responseCode = "500", description = "Failed to save settings due to internal error",
            content = @Content(schema = @Schema(example = "{\"success\":false,\"error\":\"...\"}")))
    })
    public ResponseEntity<Map<String, Object>> saveSettings(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Settings update request with arbitrary key-value pairs. Supported special keys: 'mode' or 'CLAUDE_MEM_MODE' (sets active mode). Any other valid key will be persisted to the settings file.",
                required = true,
                content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiRequests.SettingsUpdateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody com.ablueforce.cortexce.dto.ApiRequests.SettingsUpdateRequest updates) {
        try {
            settingsService.updateSettings(updates);

            // If mode changed, update ModeService
            if (updates.containsKey("mode") || updates.containsKey("CLAUDE_MEM_MODE")) {
                String newMode = updates.containsKey("mode")
                    ? Objects.toString(updates.get("mode"), "")
                    : Objects.toString(updates.get("CLAUDE_MEM_MODE"), "");
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
        @ApiResponse(responseCode = "200", description = "Timeline retrieved (returns list of timeline entries with date, count, and ids, or observations list for anchor queries)",
            content = @Content(schema = @Schema(example = "[{\"date\":\"2025-01-02\",\"count\":5,\"ids\":[\"550e8400-e29b-41d4-a716-446655440000\"]}]"))),
        @ApiResponse(responseCode = "400", description = "Date range exceeds 1 year maximum or invalid anchor parameters",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Timeline query failed",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class)))
    })
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<Object> getTimeline(
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
            return (ResponseEntity<Object>) timelineService.getTimelineByAnchor(project, anchorId, query, depthBefore, depthAfter);
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
        @ApiResponse(responseCode = "200", description = "Search results returned",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.SearchByFileResponse.class))),
        @ApiResponse(responseCode = "500", description = "Search failed due to internal error",
            content = @Content(schema = @Schema(implementation = com.ablueforce.cortexce.dto.ApiResponses.ErrorResponse.class)))
    })
    public ResponseEntity<com.ablueforce.cortexce.dto.ApiResponses.SearchByFileResponse> searchByFile(
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

            return ResponseEntity.ok(new com.ablueforce.cortexce.dto.ApiResponses.SearchByFileResponse(
                observations, observations.size(), filePath, isFolder
            ));
        } catch (Exception e) {
            log.error("Search by file failed for project={}, filePath={}: {}", project, filePath, e.getMessage());
            return ResponseEntity.status(500).body(new com.ablueforce.cortexce.dto.ApiResponses.SearchByFileResponse(
                List.of(), 0, filePath, isFolder
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
    @ApiResponse(responseCode = "200", description = "Sessions retrieved (empty list if no matching sessions found)",
        content = @Content(schema = @Schema(example = "[{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"content_session_id\":\"session-1\",\"project\":\"/path\",\"user_prompt\":\"hello\",\"started_at_epoch\":1709000000000,\"completed_at_epoch\":1709000060000,\"status\":\"completed\"}]")))
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
     *
     * ⚠️ WEBUI COMPATIBILITY: "hasMore" MUST stay camelCase — the WebUI submodule
     * (webui/src/ui/viewer/hooks/usePagination.ts) reads data.hasMore directly.
     * Do NOT change to "has_more" without updating the WebUI first.
     */
    public record PagedResponse<T>(
        @Schema(description = "Page items")
        @JsonProperty("items") List<T> items,
        @Schema(description = "Whether more pages are available", example = "true")
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
