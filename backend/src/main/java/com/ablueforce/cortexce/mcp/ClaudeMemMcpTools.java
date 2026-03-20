package com.ablueforce.cortexce.mcp;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.service.EmbeddingService;
import com.ablueforce.cortexce.service.SearchService;
import com.ablueforce.cortexce.service.TimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MCP Tool definitions for Claude-Mem.
 * <p>
 * P1: Thin wrapper layer that delegates to underlying Services.
 * Following "thin layer" architecture: REST and MCP layers are thin,
 * business logic stays in Service layer.
 * <p>
 * Tools provided:
 * - search: Step 1 - Search memory, returns index with IDs
 * - timeline: Step 2 - Get context around anchor point
 * - get_observations: Step 3 - Fetch full details by IDs
 * - save_memory: Save manual memory for future retrieval
 */
@Component
public class ClaudeMemMcpTools {

    private static final Logger log = LoggerFactory.getLogger(ClaudeMemMcpTools.class);

    private final SearchService searchService;
    private final EmbeddingService embeddingService;
    private final ObservationRepository observationRepository;
    private final TimelineService timelineService;
    private final SummaryRepository summaryRepository;
    private final SessionRepository sessionRepository;

    public ClaudeMemMcpTools(
            SearchService searchService,
            EmbeddingService embeddingService,
            ObservationRepository observationRepository,
            TimelineService timelineService,
            SummaryRepository summaryRepository,
            SessionRepository sessionRepository) {
        this.searchService = searchService;
        this.embeddingService = embeddingService;
        this.observationRepository = observationRepository;
        this.timelineService = timelineService;
        this.summaryRepository = summaryRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * MCP Tool: Search memory for observations.
     * Step 1 in the 3-step memory retrieval workflow.
     */
    @McpTool(
        name = "search",
        description = "Step 1: Search memory. Returns index with IDs. Use query for semantic search or type/concept for filtering."
    )
    public Map<String, Object> search(
            @McpToolParam(description = "Search query for semantic search", required = false) String query,
            @McpToolParam(description = "Project path filter", required = true) String project,
            @McpToolParam(description = "Max results (default: 20)", required = false) Integer limit,
            @McpToolParam(description = "Observation type filter", required = false) String type,
            @McpToolParam(description = "Observation concept filter", required = false) String concept,
            @McpToolParam(description = "Pagination offset", required = false) Integer offset,
            @McpToolParam(description = "Sort field (e.g., 'created_at_epoch')", required = false) String orderBy
    ) {
        log.info("MCP search: query={}, project={}, limit={}", query, project, limit);

        int effectiveLimit = limit != null ? limit : 20;

        float[] queryVector = null;
        if (query != null && !query.isBlank()) {
            try {
                queryVector = embeddingService.embed(query);
            } catch (Exception e) {
                log.warn("Embedding failed for query, falling back to text search: {}", e.getMessage());
            }
        }

        SearchService.SearchResult result = searchService.search(
            new SearchService.SearchRequest(project, query, queryVector, type, concept, null, null, null, effectiveLimit)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("observations", result.observations());
        response.put("strategy", result.strategy());
        response.put("fell_back", result.fellBack());
        response.put("count", result.observations().size());
        return response;
    }

    /**
     * MCP Tool: Get timeline context around an anchor point.
     * Step 2 in the 3-step memory retrieval workflow.
     * E.5 Fix: Uses getTimelineMap() directly instead of unwrapping ResponseEntity.
     */
    @McpTool(
        name = "timeline",
        description = "Step 2: Get context around results. Provide anchor ID or query to find anchor, then get surrounding observations."
    )
    public Map<String, Object> timeline(
            @McpToolParam(description = "Project path filter", required = true) String project,
            @McpToolParam(description = "Anchor observation ID to get context around", required = false) String anchorId,
            @McpToolParam(description = "Query to find best matching anchor", required = false) String query,
            @McpToolParam(description = "Number of items before anchor (default: 5)", required = false) Integer depthBefore,
            @McpToolParam(description = "Number of items after anchor (default: 5)", required = false) Integer depthAfter
    ) {
        log.info("MCP timeline: project={}, anchorId={}, query={}", project, anchorId, query);

        try {
            // E.5 Fix: Call getTimelineMap() directly, no ResponseEntity unwrapping needed
            return timelineService.getTimelineMap(project, anchorId, query, depthBefore, depthAfter);
        } catch (Exception e) {
            log.error("Timeline query failed: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            errorResponse.put("observations", List.of());
            return errorResponse;
        }
    }

    /**
     * MCP Tool: Get full observation details by IDs.
     * Step 3 in the 3-step memory retrieval workflow.
     */
    @McpTool(
        name = "get_observations",
        description = "Step 3: Fetch full details for filtered IDs. Provide list of observation IDs to retrieve complete observation data."
    )
    public Map<String, Object> getObservations(
            @McpToolParam(description = "Array of observation IDs (required)", required = true) List<String> ids,
            @McpToolParam(description = "Project path filter", required = false) String project,
            @McpToolParam(description = "Sort field (e.g., 'created_at_epoch')", required = false) String orderBy,
            @McpToolParam(description = "Maximum number of results", required = false) Integer limit
    ) {
        log.info("MCP get_observations: ids count={}, project={}", ids != null ? ids.size() : 0, project);

        Map<String, Object> response = new HashMap<>();

        if (ids == null || ids.isEmpty()) {
            response.put("error", "Missing required field: ids");
            response.put("observations", List.of());
            response.put("count", 0);
            return response;
        }

        // Convert string IDs to UUIDs
        List<UUID> uuids = ids.stream()
            .map(id -> {
                try {
                    return UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID format: {}", id);
                    return null;
                }
            })
            .filter(id -> id != null)
            .collect(Collectors.toList());

        List<ObservationEntity> observations = observationRepository.findAllById(uuids);

        // Apply project filter if specified
        if (project != null && !project.isBlank()) {
            observations = observations.stream()
                .filter(o -> project.equals(o.getProjectPath()))
                .collect(Collectors.toList());
        }

        // Apply ordering if specified
        if ("created_at_epoch".equals(orderBy) || "createdAtEpoch".equals(orderBy)) {
            observations = observations.stream()
                .sorted((a, b) -> Long.compare(
                    b.getCreatedAtEpoch() != null ? b.getCreatedAtEpoch() : 0,
                    a.getCreatedAtEpoch() != null ? a.getCreatedAtEpoch() : 0
                ))
                .collect(Collectors.toList());
        }

        // Apply limit if specified
        if (limit != null && limit > 0 && observations.size() > limit) {
            observations = observations.subList(0, limit);
        }

        response.put("observations", observations);
        response.put("count", observations.size());
        return response;
    }

    /**
     * MCP Tool: Save a manual memory/observation.
     * Allows users to manually store important information for future retrieval.
     */
    @McpTool(
        name = "save_memory",
        description = "Save a manual memory/observation for semantic search. Use this to store important information you want to remember."
    )
    public Map<String, Object> saveMemory(
            @McpToolParam(description = "Content to remember (required)", required = true) String text,
            @McpToolParam(description = "Short title for the memory", required = false) String title,
            @McpToolParam(description = "Project path", required = false) String project
    ) {
        log.info("MCP save_memory: title={}, project={}", title, project);

        Map<String, Object> response = new HashMap<>();

        if (text == null || text.isBlank()) {
            response.put("success", false);
            response.put("error", "Text content is required");
            return response;
        }

        try {
            // Generate unique session ID for manual memory
            String contentSessionId = "manual-" + System.currentTimeMillis();

            // E.1 Fix: Create dummy session first to satisfy FK constraint
            SessionEntity dummySession = new SessionEntity();
            dummySession.setContentSessionId(contentSessionId);
            dummySession.setProjectPath(project != null ? project : "manual-memories");
            dummySession.setStartedAtEpoch(System.currentTimeMillis());
            dummySession.setStatus("completed");
            sessionRepository.save(dummySession);
            log.debug("Created dummy session for manual memory: {}", contentSessionId);

            // Create observation entity
            // TS alignment: Use type='discovery' and subtitle='Manual memory' like MemoryRoutes.ts
            ObservationEntity observation = new ObservationEntity();
            observation.setContent(text);
            observation.setTitle(title != null ? title : "Manual Memory");
            observation.setSubtitle("Manual memory");
            observation.setProjectPath(project);
            observation.setType("discovery");  // TS uses 'discovery' type
            observation.setContentSessionId(contentSessionId);
            observation.setCreatedAtEpoch(System.currentTimeMillis());
            observation.setPromptNumber(0);
            observation.setDiscoveryTokens(0);

            // Generate embedding
            float[] embedding = embeddingService.embed(text);
            observation.setEmbedding1024(embedding);
            observation.setEmbeddingModelId("bge-m3");

            // Save
            ObservationEntity saved = observationRepository.save(observation);

            response.put("success", true);
            response.put("id", saved.getId().toString());
            response.put("message", "Memory saved successfully");
        } catch (Exception e) {
            log.error("Failed to save memory: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Failed to save memory: " + e.getMessage());
        }

        return response;
    }

    /**
     * MCP Tool: Get recent context (summaries for a project).
     * Returns recent sessions with their summaries for context display.
     */
    @McpTool(
        name = "recent",
        description = "Get recent session context (summaries). Quick access to what you were working on recently. Params: project, limit (default: 3)"
    )
    public Map<String, Object> recent(
            @McpToolParam(description = "Project path filter", required = true) String project,
            @McpToolParam(description = "Number of recent sessions (default: 3)", required = false) Integer limit) {

        int effectiveLimit = limit != null ? limit : 3;
        log.info("MCP recent: project={}, limit={}", project, effectiveLimit);

        Map<String, Object> response = new HashMap<>();

        if (project == null || project.isBlank()) {
            response.put("content", List.of(Map.of(
                    "type", "text",
                    "text", "# Recent Session Context\n\nProject parameter is required."
            )));
            response.put("isError", true);
            return response;
        }

        // Get recent summaries for the project
        List<SummaryEntity> summaries = summaryRepository.findByProjectLimited(project, effectiveLimit);

        if (summaries.isEmpty()) {
            response.put("content", List.of(Map.of(
                    "type", "text",
                    "text", "# Recent Session Context\n\nNo previous sessions found for project \"" + project + "\"."
            )));
            response.put("count", 0);
            return response;
        }

        // Format summaries for display
        StringBuilder text = new StringBuilder();
        text.append("# Recent Session Context\n\n");
        text.append("Showing last ").append(summaries.size()).append(" session(s) for **").append(project).append("**:\n\n");

        for (SummaryEntity summary : summaries) {
            text.append("---\n\n");
            text.append("**Summary**\n\n");

            if (summary.getRequest() != null && !summary.getRequest().isBlank()) {
                text.append("**Request:** ").append(summary.getRequest()).append("\n");
            }
            if (summary.getCompleted() != null && !summary.getCompleted().isBlank()) {
                text.append("**Completed:** ").append(summary.getCompleted()).append("\n");
            }
            if (summary.getLearned() != null && !summary.getLearned().isBlank()) {
                text.append("**Learned:** ").append(summary.getLearned()).append("\n");
            }
            if (summary.getNextSteps() != null && !summary.getNextSteps().isBlank()) {
                text.append("**Next Steps:** ").append(summary.getNextSteps()).append("\n");
            }
            text.append("\n");
        }

        response.put("content", List.of(Map.of(
                "type", "text",
                "text", text.toString()
        )));
        response.put("count", summaries.size());
        return response;
    }
}
