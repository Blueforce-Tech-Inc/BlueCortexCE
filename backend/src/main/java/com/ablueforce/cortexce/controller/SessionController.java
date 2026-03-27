package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.service.SessionManagementService;
import com.ablueforce.cortexce.service.ClaudeMdService;
import com.ablueforce.cortexce.service.ContextCacheService;
import com.ablueforce.cortexce.service.ContextService;
import com.ablueforce.cortexce.service.StructuredExtractionService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Session controller — handles session lifecycle and context injection.
 *
 * This controller manages:
 * - Session initialization (start new or resume existing)
 * - Context generation for Claude Code injection
 * - CLAUDE.md updates
 *
 * Note: Session completion and summary generation is handled by
 * IngestionController.handleSessionEnd() via wrapper.js session-end hook.
 *
 * Endpoint mapping (wrapper.js -> this controller):
 *   wrapper.js session-start  -> /api/session/start
 */
@RestController
@RequestMapping("/api/session")
@Tag(name = "Session", description = "Session lifecycle management and context injection for Claude Code hooks")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionRepository sessionRepository;
    private final SessionManagementService sessionManagementService;
    private final ContextService contextService;
    private final ContextCacheService contextCacheService;
    private final ClaudeMdService claudeMdService;
    private final Optional<StructuredExtractionService> extractionService;

    public SessionController(SessionRepository sessionRepository,
                             SessionManagementService sessionManagementService,
                             ContextService contextService,
                             ContextCacheService contextCacheService,
                             ClaudeMdService claudeMdService,
                             Optional<StructuredExtractionService> extractionService) {
        this.sessionRepository = sessionRepository;
        this.sessionManagementService = sessionManagementService;
        this.contextService = contextService;
        this.contextCacheService = contextCacheService;
        this.claudeMdService = claudeMdService;
        this.extractionService = extractionService;
    }

    // ==========================================================================
    // Session Lifecycle
    // ==========================================================================

    /**
     * Start a new session or resume existing session.
     *
     * This endpoint combines:
     * - Session initialization (creates or retrieves session)
     * - Context generation (for Claude Code context injection)
     * - CLAUDE.md update (if needed)
     *
     * Called by: wrapper.js session-start (on Claude Code compaction)
     *
     * POST /api/session/start
     * {
     *   "session_id": "content-session-id",
     *   "project_path": "/path/to/project",
     *   "cwd": "/path/to/project",
     *   "projects": "project1,project2",  // Optional: for worktree support
     *   "is_worktree": true,               // Optional: worktree flag
     *   "parent_project": "parent"         // Optional: parent project name
     * }
     *
     * Returns:
     * {
     *   "context": "...",
     *   "updateFiles": [...],
     *   "session_db_id": "uuid",
     *   "prompt_number": 1
     * }
     */
    @PostMapping(value = "/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start or resume a session",
        description = "Initializes a new session or retrieves an existing one. Generates context for Claude Code injection and optionally updates CLAUDE.md files. Supports worktree multi-project context via the 'projects' parameter. Called by wrapper.js session-start hook.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Session started successfully, returns context and updateFiles"),
        @ApiResponse(responseCode = "400", description = "Missing required fields: session_id and project_path (or cwd)"),
        @ApiResponse(responseCode = "500", description = "Failed to initialize session due to internal error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Session start payload. Fields: session_id (required, unique session identifier), project_path (required, absolute path), user_id (optional, for multi-user), projects (optional, comma-separated paths for worktree)")
    public ResponseEntity<Map<String, Object>> startSession(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        String contentSessionId = (String) body.get("session_id");
        String projectPath = (String) body.get("project_path");
        String projectPathFromCwd = (String) body.get("cwd"); // fallback when project_path absent
        String projectsParam = (String) body.get("projects");
        Boolean isWorktree = (Boolean) body.get("is_worktree");
        String parentProject = (String) body.get("parent_project");
        String userId = (String) body.get("user_id");  // Phase 3: optional user identifier

        // P1: Validate required fields
        if (contentSessionId == null || contentSessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: session_id"));
        }
        if (projectPath == null || projectPath.isBlank()) {
            projectPath = projectPathFromCwd; // fallback to cwd for API compatibility
        }
        if (projectPath == null || projectPath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: project_path (or cwd)"));
        }

        // Log worktree info if present
        if (Boolean.TRUE.equals(isWorktree)) {
            log.info("Session start (worktree): session_id={}, project={}, parent={}",
                contentSessionId, projectPath, parentProject);
        } else {
            log.info("Session start: session_id={}, project_path={}", contentSessionId, projectPath);
        }

        // 1. Initialize or retrieve session
        SessionEntity session;
        try {
            session = sessionManagementService.initializeSession(contentSessionId, projectPath, null);
        } catch (Exception e) {
            log.error("Failed to initialize session {}: {}", contentSessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to initialize session: " + e.getMessage()));
        }
        
        // Phase 3: Set userId on session (null is OK — means single-user mode)
        if (userId != null && !userId.isBlank() && session.getUserId() == null) {
            session.setUserId(userId);
            sessionManagementService.save(session);
        }
        String sessionDbId = session.getId().toString();

        // 2. Generate context from observations (try cache first)
        String context = "";
        if (projectPath != null && !projectPath.isEmpty()) {
            // Try to get cached context first (fast path)
            context = contextCacheService.getContextIfFresh(projectPath);

            if (context == null) {
                // Cache miss or stale - generate fresh context
                // Check if multiple projects (worktree support)
                if (projectsParam != null && !projectsParam.isBlank() && projectsParam.contains(",")) {
                    // Multi-project context query (worktree mode)
                    List<String> projects = parseProjectsParam(projectsParam);
                    context = contextService.generateContextMultiProject(
                        projects,
                        new ContextService.ContextConfig()
                    );
                    log.debug("Generated multi-project context for {} projects", projects.size());
                } else {
                    // Single project context
                    context = contextService.generateContext(
                        projectPath,
                        new ContextService.ContextConfig(),
                        contentSessionId
                    );
                    log.debug("Generated fresh context for project {} (cache miss)", projectPath);
                }

                // Update cache for this project
                cacheContextForProject(projectPath, context);
            } else {
                log.debug("Returned cached context for project {}", projectPath);
            }
        }

        // 3. Generate CLAUDE.md update if needed
        List<Map<String, String>> updateFiles = new ArrayList<>();
        if (projectPath != null && !projectPath.isEmpty()) {
            Path claudeMdPath = findClaudeMdInProject(projectPath);
            if (claudeMdPath != null) {
                String claudeMdContent = claudeMdService.generateClaudeMd(projectPath);
                updateFiles.add(Map.of(
                    "path", claudeMdPath.toString(),
                    "content", claudeMdContent
                ));
            }
        }

        log.debug("Session start completed: session_db_id={}, context_length={}, updateFiles_count={}",
            sessionDbId, context.length(), updateFiles.size());

        return ResponseEntity.ok(Map.of(
            "context", context,
            "updateFiles", updateFiles,
            "session_db_id", sessionDbId,
            "prompt_number", 1
        ));
    }

    /**
     * Get session information by content session ID.
     *
     * GET /api/session/{sessionId}
     *
     * Returns session details or error if not found.
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get session by ID",
        description = "Retrieves session details by content session ID (the ID used by Claude Code). Returns session metadata including project path, status, and start time.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Session found and returned"),
        @ApiResponse(responseCode = "404", description = "Session not found for the given content session ID"),
        @ApiResponse(responseCode = "500", description = "Internal error retrieving session")
    })
    public ResponseEntity<Map<String, Object>> getSession(
            @Parameter(description = "Content session ID (Claude Code session identifier)", required = true, example = "sess-abc123")
            @PathVariable String sessionId) {
        try {
            Optional<SessionEntity> session = sessionRepository.findByContentSessionId(sessionId);

            if (session.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found", "session_id", sessionId));
            }

            SessionEntity s = session.get();
            return ResponseEntity.ok(Map.of(
                "session_db_id", s.getId().toString(),
                "content_session_id", s.getContentSessionId() != null ? s.getContentSessionId() : "",
                "project_path", s.getProjectPath() != null ? s.getProjectPath() : "",
                "status", s.getStatus() != null ? s.getStatus() : "",
                "started_at", s.getStartedAt() != null ? s.getStartedAt().toString() : ""
            ));
        } catch (Exception e) {
            log.error("Failed to get session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get session: " + e.getMessage()));
        }
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    /**
     * P2: Cache the generated context for the project.
     * Logs and continues on failure - caching is best-effort.
     */
    private void cacheContextForProject(String projectPath, String context) {
        List<SessionEntity> sessions = sessionRepository.findByProjectPathAndStatus(projectPath, "active");
        if (!sessions.isEmpty()) {
            SessionEntity activeSession = sessions.get(0);
            activeSession.setCachedContext(context);
            activeSession.setContextRefreshedAtEpoch(System.currentTimeMillis());
            try {
                sessionRepository.save(activeSession);
            } catch (Exception e) {
                // P2: Log and continue - caching is best-effort, context can be regenerated
                log.warn("Failed to cache context for project {}: {}", projectPath, e.getMessage());
            }
        }
    }

    /**
     * Find CLAUDE.md in project directory or its parents.
     * P0: Validates paths to prevent escaping project root via symlinks.
     *
     * @return Path to CLAUDE.md, or null if not found
     */
    private Path findClaudeMdInProject(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }

        // P0: Normalize and resolve the path to prevent path traversal
        Path basePath = Path.of(projectPath).toAbsolutePath().normalize();
        Path claudeMdPath = basePath.resolve("CLAUDE.md");

        // Validate the resolved path is within the project
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
        Path rootPath = basePath.getRoot();
        Path current = basePath.getParent();

        // P0: Limit traversal depth and validate each step
        int maxDepth = 10;
        int depth = 0;

        while (current != null && !current.equals(rootPath) && depth < maxDepth) {
            Path candidate = current.resolve("CLAUDE.md");

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

            // Check for project root marker (.git directory)
            try {
                Path gitPath = current.resolve(".git");
                if (java.nio.file.Files.exists(gitPath)) {
                    // Found project root, stop searching
                    return null;
                }
            } catch (SecurityException e) {
                log.warn("Cannot access .git at {}: {}", current, e.getMessage());
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
    private boolean isWithinProject(Path projectRoot, Path targetPath) {
        try {
            Path normalizedTarget = targetPath.toAbsolutePath().normalize();
            Path normalizedRoot = projectRoot.toAbsolutePath().normalize();

            // Check if the normalized target starts with the project root
            // or is the same path
            return normalizedTarget.startsWith(normalizedRoot)
                || normalizedTarget.equals(normalizedRoot);
        } catch (SecurityException e) {
            log.warn("Security exception checking path bounds: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Update userId for an existing session. Phase 3 multi-user support.
     * PATCH /api/session/{sessionId}/user
     */
    @PatchMapping("/{sessionId}/user")
    @Operation(summary = "Update session user ID",
        description = "Updates the user identifier for an existing session. Used for Phase 3 multi-user support. Triggers re-extraction of user-scoped results when userId changes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User ID updated successfully"),
        @ApiResponse(responseCode = "404", description = "Session not found for the given content session ID")
    })
    public ResponseEntity<Map<String, String>> updateSessionUserId(
            @Parameter(description = "Content session ID to update", required = true, example = "sess-abc123")
            @PathVariable String sessionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User ID update payload. Fields: user_id (required, the new user identifier)")
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        String userId = (String) body.get("user_id");
        
        SessionEntity session = sessionManagementService.findByContentSessionId(sessionId)
            .orElse(null);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Session not found: " + sessionId));
        }
        
        String oldUserId = session.getUserId();
        session.setUserId(userId);
        sessionManagementService.save(session);
        
        log.info("Updated session {} userId: {} -> {}", sessionId, oldUserId, userId);

        // Phase 3: Re-run extraction when userId changes (user-scoped results need updating)
        if (extractionService.isPresent() && session.getProjectPath() != null) {
            try {
                extractionService.get().reExtractForSession(sessionId, session.getProjectPath());
                log.info("Re-extraction triggered for session {} after userId update", sessionId);
            } catch (Exception e) {
                log.warn("Re-extraction failed for session {} after userId update: {}", sessionId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "sessionId", sessionId,
            "userId", userId != null ? userId : ""
        ));
    }

    /**
     * Parse comma-separated projects parameter.
     * Used for worktree multi-project context queries.
     *
     * @param projectsParam Comma-separated project names (e.g., "parent,worktree")
     * @return List of project names
     */
    private List<String> parseProjectsParam(String projectsParam) {
        if (projectsParam == null || projectsParam.isBlank()) {
            return new ArrayList<>();
        }
        List<String> projects = new ArrayList<>();
        for (String project : projectsParam.split(",")) {
            String trimmed = project.trim();
            if (!trimmed.isEmpty()) {
                projects.add(trimmed);
            }
        }
        return projects;
    }
}
