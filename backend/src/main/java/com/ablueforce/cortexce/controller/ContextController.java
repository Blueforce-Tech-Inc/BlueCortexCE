package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.service.ClaudeMdService;
import com.ablueforce.cortexce.service.ContextService;
import com.ablueforce.cortexce.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context injection controller - provides context for Claude Code hooks.
 *
 * Endpoints mirror the TypeScript version's /api/context/inject:
 * - Returns context as plain text for stdout injection
 * - Returns updateFiles for CLAUDE.md updates when needed
 */
@RestController
@RequestMapping("/api/context")
@Tag(name = "Context", description = "Context generation and injection endpoints for Claude Code sessions")
public class ContextController {

    private static final Logger log = LoggerFactory.getLogger(ContextController.class);

    private final ContextService contextService;
    private final ClaudeMdService claudeMdService;
    private final TimelineService timelineService;
    private final SummaryRepository summaryRepository;

    public ContextController(ContextService contextService,
                             ClaudeMdService claudeMdService,
                             TimelineService timelineService,
                             SummaryRepository summaryRepository) {
        this.contextService = contextService;
        this.claudeMdService = claudeMdService;
        this.timelineService = timelineService;
        this.summaryRepository = summaryRepository;
    }

    /**
     * Generate context for injection into Claude Code session.
     *
     * GET /api/context/inject?projects=project1,project2
     *
     * Returns:
     * - Plain text context (for stdout output)
     * - updateFiles array (if CLAUDE.md needs updating)
     */
    @GetMapping(value = "/inject", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inject context for Claude Code session",
        description = "Generates context from observations for one or more projects. Returns plain text context for stdout injection and updateFiles array if CLAUDE.md needs updating. Supports comma-separated project paths for worktree mode. Falls back to current working directory if no projects specified.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Context generated successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to generate context due to internal error")
    })
    public ResponseEntity<Map<String, Object>> injectContext(
            @Parameter(description = "Comma-separated project paths for context injection (e.g., '/path/to/project1,/path/to/project2'). Empty string uses current working directory.", required = false, example = "/Users/dev/my-project")
            @RequestParam(required = false, defaultValue = "") String projects) {

        log.debug("Context inject request, projects: {}", projects);

        try {
            // Parse projects (comma-separated for worktree support)
            String[] projectList = projects.isEmpty() ? new String[0] : projects.split(",");

            // P2: Validate each project path
            List<String> validPaths = new java.util.ArrayList<>();
            for (String projectPath : projectList) {
                String trimmedPath = projectPath.trim();
                if (trimmedPath.isEmpty()) continue;
                // Validate path format
                if (!trimmedPath.startsWith("/") && !trimmedPath.matches("^[A-Za-z]:\\\\")) {
                    log.warn("Invalid project path format: {}", trimmedPath);
                    continue;
                }
                validPaths.add(trimmedPath);
            }

            StringBuilder contextBuilder = new StringBuilder();
            List<Map<String, String>> updateFiles = new java.util.ArrayList<>();

            for (String projectPath : validPaths) {
                try {
                    // Generate context for this project
                    String context = contextService.generateContext(projectPath);
                    if (contextBuilder.length() > 0) {
                        contextBuilder.append("\n---\n\n");
                    }
                    contextBuilder.append(context);

                    // Check if CLAUDE.md needs update
                    Path claudeMdPath = findClaudeMdInProject(projectPath);
                    if (claudeMdPath != null) {
                        String claudeMdContent = claudeMdService.generateClaudeMd(projectPath);
                        updateFiles.add(Map.of(
                                "path", claudeMdPath.toString(),
                                "content", claudeMdContent
                        ));
                    }
                } catch (Exception e) {
                    log.error("Failed to generate context for project {}: {}", projectPath, e.getMessage());
                    if (contextBuilder.length() > 0) {
                        contextBuilder.append("\n---\n\n");
                    }
                    contextBuilder.append("[Error generating context for ").append(projectPath).append("]");
                }
            }

            String finalContext = contextBuilder.toString();

            // If no projects specified, try current working directory
            // P1: Validate cwd is a safe directory before use
            if (projectList.length == 0) {
                String cwd = System.getProperty("user.dir");
                // P1: Only use cwd if it's a valid, safe path
                if (cwd != null && !cwd.isBlank() && isSafeDirectory(cwd)) {
                    finalContext = contextService.generateContext(cwd);
                    log.debug("Generated context for cwd: {}", cwd);
                    log.info("No projects specified, using current working directory: {}", cwd);
                } else {
                    log.warn("Current working directory is not safe: {}", cwd);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "context", finalContext,
                    "updateFiles", updateFiles
            ));
        } catch (Exception e) {
            log.error("Failed to inject context: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to inject context: " + e.getMessage()
            ));
        }
    }

    /**
     * Get recent context (summaries and observations for a project).
     * TS Alignment: GET /api/context/recent?project=...&limit=3
     *
     * Returns recent sessions with their summaries for context display.
     */
    @GetMapping(value = "/recent", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get recent session context",
        description = "Returns recent session summaries for a project, formatted as markdown for display. Used to provide historical context at the start of new sessions. Defaults to current directory basename if project not specified.")
    @ApiResponse(responseCode = "200", description = "Recent context retrieved (may contain empty content if no sessions found)")
    public ResponseEntity<?> getRecentContext(
            @Parameter(description = "Project name to query. Defaults to current directory basename if not specified.", required = false, example = "my-project")
            @RequestParam(required = false) String project,
            @Parameter(description = "Maximum number of recent sessions to return", required = false, example = "3")
            @RequestParam(required = false, defaultValue = "3") int limit) {

        // Default to current directory basename if project not specified
        String projectName = project;
        if (projectName == null || projectName.isBlank()) {
            projectName = Paths.get(System.getProperty("user.dir")).getFileName().toString();
        }

        log.debug("Recent context request, project: {}, limit: {}", projectName, limit);

        // Get recent summaries for the project
        List<SummaryEntity> summaries = summaryRepository.findByProjectLimited(projectName, limit);

        if (summaries.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("content", List.of(Map.of(
                    "type", "text",
                    "text", "# Recent Session Context\n\nNo previous sessions found for project \"" + projectName + "\"."
            )));
            return ResponseEntity.ok(response);
        }

        // Format summaries for display
        StringBuilder text = new StringBuilder();
        text.append("# Recent Session Context\n\n");
        text.append("Showing last ").append(summaries.size()).append(" session(s) for **").append(projectName).append("**:\n\n");

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

        Map<String, Object> response = new HashMap<>();
        response.put("content", List.of(Map.of(
                "type", "text",
                "text", text.toString()
        )));
        response.put("count", summaries.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Get context timeline around an anchor point.
     * TS Alignment: GET /api/context/timeline?anchor=123&depth_before=10&depth_after=10&project=...
     *
     * Returns observations around a specified anchor point (by ID, session, or timestamp).
     */
    @GetMapping(value = "/timeline", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get context timeline around anchor point",
        description = "Returns observations around a specified anchor point (by ID, session, or timestamp). Used for exploring context around specific observations. Delegates to TimelineService which handles anchor resolution.")
    @ApiResponse(responseCode = "200", description = "Timeline retrieved successfully")
    public ResponseEntity<?> getContextTimeline(
            @Parameter(description = "Anchor point: observation UUID, session ID, or query string to find anchor", required = false, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestParam(required = false) String anchor,
            @Parameter(description = "Number of observations to return before the anchor point", required = false, example = "10")
            @RequestParam(required = false, defaultValue = "10") int depth_before,
            @Parameter(description = "Number of observations to return after the anchor point", required = false, example = "10")
            @RequestParam(required = false, defaultValue = "10") int depth_after,
            @Parameter(description = "Project path to filter observations", required = false, example = "/Users/dev/my-project")
            @RequestParam(required = false) String project) {

        log.debug("Context timeline request, anchor: {}, project: {}, before: {}, after: {}",
                anchor, project, depth_before, depth_after);

        // Delegate to TimelineService which handles anchor resolution
        // The service supports anchorId (UUID string) or query-based anchor finding
        return timelineService.getTimelineByAnchor(project, anchor, null, depth_before, depth_after);
    }

    /**
     * Generate context for a single project (simpler endpoint).
     *
     * POST /api/context/generate
     * Body: { "project_path": "/path/to/project" }
     */
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Generate context for a project",
        description = "Generates context for a single project. Simpler alternative to /inject endpoint. Falls back to current working directory if project_path is not provided in the request body.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Context generated successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to generate context for the specified project")
    })
    public ResponseEntity<Map<String, String>> generateContext(
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        String projectPath = body.get("project_path");
        if (projectPath == null || projectPath.isEmpty()) {
            projectPath = System.getProperty("user.dir");
        }

        try {
            String context = contextService.generateContext(projectPath);
            return ResponseEntity.ok(Map.of("context", context));
        } catch (Exception e) {
            log.error("Failed to generate context for project {}: {}", projectPath, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to generate context: " + e.getMessage()
            ));
        }
    }

    /**
     * Preview context for a project - returns formatted plain text for UI display.
     *
     * GET /api/context/preview?project=/path/to/project&observationTypes=bugfix,feature&concepts=how-it-works
     *
     * Returns: Plain text context formatted for display in the WebUI.
     * This matches what useContextPreview.ts expects: response.text() for display.
     *
     * Query Parameters:
     * - project: Project path (required)
     * - observationTypes: Comma-separated list of types to include (optional, empty = all)
     * - concepts: Comma-separated list of concepts to filter by (optional, empty = all)
     * - includeObservations: Include observations in preview (default: true)
     * - includeSummaries: Include summaries in preview (default: true)
     * - maxObservations: Maximum observations to include (default: 50)
     * - maxSummaries: Maximum summaries to include (default: 2)
     * - sessionCount: Number of recent sessions to query from (default: 10)
     * - fullCount: Number of observations to show full details (default: 5)
     */
    @GetMapping(value = "/preview", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Preview context for display",
        description = "Generates a formatted plain text context preview for WebUI display. Supports filtering by observation types, concepts, and configurable limits for observations, summaries, sessions, and full-detail count. Returns plain text (not JSON).")
    @ApiResponse(responseCode = "200", description = "Plain text context preview generated")
    public String previewContext(
            @Parameter(description = "Project path to generate preview for (required, must be absolute path)", required = true, example = "/Users/dev/my-project")
            @RequestParam String project,
            @Parameter(description = "Comma-separated observation types to include (empty = all)", required = false, example = "bugfix,feature")
            @RequestParam(required = false, defaultValue = "") String observationTypes,
            @Parameter(description = "Comma-separated concepts to filter by (empty = all)", required = false, example = "how-it-works,architecture")
            @RequestParam(required = false, defaultValue = "") String concepts,
            @Parameter(description = "Include observations in preview", required = false, example = "true")
            @RequestParam(required = false, defaultValue = "true") boolean includeObservations,
            @Parameter(description = "Include summaries in preview", required = false, example = "true")
            @RequestParam(required = false, defaultValue = "true") boolean includeSummaries,
            @Parameter(description = "Maximum number of observations to include", required = false, example = "50")
            @RequestParam(required = false, defaultValue = "50") int maxObservations,
            @Parameter(description = "Maximum number of summaries to include", required = false, example = "2")
            @RequestParam(required = false, defaultValue = "2") int maxSummaries,
            @Parameter(description = "Number of recent sessions to query from", required = false, example = "10")
            @RequestParam(required = false, defaultValue = "10") int sessionCount,
            @Parameter(description = "Number of observations to show full details for", required = false, example = "5")
            @RequestParam(required = false, defaultValue = "5") int fullCount) {

        log.debug("Context preview request, project: {}, types: {}, concepts: {}, includeObs: {}, includeSum: {}, maxObs: {}, sessions: {}, fullCount: {}",
                project, observationTypes, concepts, includeObservations, includeSummaries, maxObservations, sessionCount, fullCount);

        // Validate project parameter
        if (project == null || project.isBlank()) {
            return "Error: Project path is required";
        }

        // Validate path format
        if (!project.startsWith("/") && !project.matches("^[A-Za-z]:\\\\")) {
            return "Error: Invalid project path format";
        }

        // Parse filter parameters
        List<String> typeList = observationTypes.isBlank()
                ? List.of()
                : java.util.Arrays.stream(observationTypes.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());

        List<String> conceptList = concepts.isBlank()
                ? List.of()
                : java.util.Arrays.stream(concepts.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());

        // Generate full context as plain text with filtering support
        try {
            return contextService.generateContextWithFilters(
                    project,
                    typeList,
                    conceptList,
                    includeObservations,
                    includeSummaries,
                    maxObservations,
                    maxSummaries,
                    sessionCount,
                    fullCount
            );
        } catch (Exception e) {
            log.error("Failed to generate context preview for project {}: {}", project, e.getMessage());
            return "Error: Failed to generate context preview";
        }
    }

    /**
     * Find CLAUDE.md in project directory.
     * P0: Validates paths to prevent escaping project root via symlinks.
     */
    private Path findClaudeMdInProject(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }

        // P0: Normalize and resolve the path to prevent path traversal
        java.nio.file.Path basePath = java.nio.file.Paths.get(projectPath).toAbsolutePath().normalize();
        java.nio.file.Path claudeMdPath = basePath.resolve("CLAUDE.md");

        // P0: Validate the resolved path is within project bounds
        if (isWithinProject(basePath, claudeMdPath)) {
            try {
                if (java.nio.file.Files.exists(claudeMdPath)) {
                    return claudeMdPath;
                }
            } catch (SecurityException e) {
                log.warn("Cannot access CLAUDE.md at {}: {}", claudeMdPath, e.getMessage());
            }
        }

        // Search parent directories but limit to reasonable depth and validate bounds
        java.nio.file.Path rootPath = basePath.getRoot();
        java.nio.file.Path current = basePath.getParent();

        // P0: Limit traversal depth and validate each step
        int maxDepth = 10;
        int depth = 0;

        while (current != null && !current.equals(rootPath) && depth < maxDepth) {
            java.nio.file.Path candidate = current.resolve("CLAUDE.md");

            // P0: Validate candidate is still within the filesystem bounds
            if (!isWithinProject(basePath, candidate)) {
                log.warn("Path traversal attempt detected: {} would escape project", candidate);
                return null;
            }

            try {
                if (java.nio.file.Files.exists(candidate)) {
                    return candidate;
                }
            } catch (SecurityException e) {
                log.warn("Cannot access candidate path {}: {}", candidate, e.getMessage());
            }
            current = current.getParent();
            depth++;
        }

        return null;
    }

    /**
     * P0: Check if the target path is within the project boundaries.
     * Prevents path traversal via symlinks or relative path components.
     */
    private boolean isWithinProject(java.nio.file.Path projectRoot, java.nio.file.Path targetPath) {
        try {
            java.nio.file.Path normalizedTarget = targetPath.toAbsolutePath().normalize();
            java.nio.file.Path normalizedRoot = projectRoot.toAbsolutePath().normalize();

            return normalizedTarget.startsWith(normalizedRoot)
                || normalizedTarget.equals(normalizedRoot);
        } catch (SecurityException e) {
            log.warn("Security exception checking path bounds: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get prior messages from the last completed session.
     * Used for context continuity across sessions.
     *
     * GET /api/context/prior-messages?project=/path/to/project&current_session_id=xxx
     *
     * Returns:
     * - userMessage: User's last prompt (optional)
     * - assistantMessage: Last assistant message for continuity
     */
    @GetMapping(value = "/prior-messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get prior session messages",
        description = "Retrieves the last user and assistant messages from the most recent completed session for a project. Used for context continuity across sessions. Returns empty strings if no prior session exists.")
    @ApiResponse(responseCode = "200", description = "Prior messages retrieved (may contain empty strings if no prior session found)")
    public Map<String, String> getPriorMessages(
            @Parameter(description = "Project path to query prior messages for", required = true, example = "/Users/dev/my-project")
            @RequestParam String project,
            @Parameter(description = "Current session ID to exclude from the search", required = false, example = "sess-abc123")
            @RequestParam(required = false, defaultValue = "") String currentSessionId) {

        // P2: Validate project parameter is not null or empty
        if (project == null || project.isBlank()) {
            log.warn("Empty project parameter in prior-messages request");
            return Map.of(
                    "userMessage", "",
                    "assistantMessage", ""
            );
        }

        log.debug("Prior messages request, project: {}, currentSessionId: {}", project, currentSessionId);

        try {
            ContextService.PriorMessages priorMessages = contextService.getPriorSessionMessages(project, currentSessionId);

            return Map.of(
                    "userMessage", priorMessages.userMessage(),
                    "assistantMessage", priorMessages.assistantMessage()
            );
        } catch (Exception e) {
            log.error("Failed to get prior messages for project {}: {}", project, e.getMessage());
            return Map.of(
                    "userMessage", "",
                    "assistantMessage", ""
            );
        }
    }

    /**
     * P1: Validate that a directory path is safe to use.
     * Checks for path traversal attempts and validates directory existence.
     *
     * @param path the directory path to validate
     * @return true if the path is safe, false otherwise
     */
    private boolean isSafeDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        try {
            java.nio.file.Path normalizedPath = java.nio.file.Paths.get(path).normalize().toAbsolutePath();

            // Check if the path exists and is a directory
            if (!java.nio.file.Files.exists(normalizedPath)) {
                log.warn("Directory does not exist: {}", path);
                return false;
            }
            if (!java.nio.file.Files.isDirectory(normalizedPath)) {
                log.warn("Path is not a directory: {}", path);
                return false;
            }

            // P0: Check for path traversal attempts in original path
            if (path.contains("..")) {
                // After normalization, verify the result is still safe
                java.nio.file.Path rawAbsolute = java.nio.file.Paths.get(path).toAbsolutePath();
                if (!normalizedPath.startsWith(rawAbsolute.getRoot())) {
                    log.warn("Path traversal attempt detected: {}", path);
                    return false;
                }
            }

            return true;
        } catch (SecurityException e) {
            log.warn("Security exception validating directory {}: {}", path, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Failed to validate directory {}: {}", path, e.getMessage());
            return false;
        }
    }
}
