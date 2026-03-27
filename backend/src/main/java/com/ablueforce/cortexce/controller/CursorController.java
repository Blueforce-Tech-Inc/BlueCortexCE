package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.ContextService;
import com.ablueforce.cortexce.service.CursorService;
import com.ablueforce.cortexce.service.CursorService.CursorProjectEntry;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CursorController - REST API for Cursor IDE integration.
 * <p>
 * Provides endpoints for:
 * - Project registry management
 * - Context file updates
 * <p>
 * Aligned with TS CursorHooksInstaller.ts functionality.
 */
@RestController
@RequestMapping("/api/cursor")
@Tag(name = "Cursor", description = "Cursor IDE integration API for project registry management and context file updates")
public class CursorController {

    private static final Logger log = LoggerFactory.getLogger(CursorController.class);

    private final CursorService cursorService;
    private final ContextService contextService;

    public CursorController(CursorService cursorService, ContextService contextService) {
        this.cursorService = cursorService;
        this.contextService = contextService;
    }

    /**
     * Register a project for auto-context updates.
     * <p>
     * POST /api/cursor/register
     * Body: { "projectName": "my-project", "workspacePath": "/path/to/workspace" }
     */
    @PostMapping("/register")
    @Operation(summary = "Register a Cursor project",
        description = "Registers a project for automatic Cursor context file updates. The context file (.cursor/rules/claude-mem-context.mdc) will be updated when new observations are recorded.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project registered successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required fields: projectName or workspacePath"),
        @ApiResponse(responseCode = "500", description = "Failed to register project due to internal error")
    })
    public ResponseEntity<Map<String, Object>> registerProject(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with projectName (unique identifier) and workspacePath (absolute path)", required = true)
        @org.springframework.web.bind.annotation.RequestBody Map<String, String> request
    ) {
        String projectName = request.get("projectName");
        String workspacePath = request.get("workspacePath");

        if (projectName == null || projectName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "projectName is required"
            ));
        }

        if (workspacePath == null || workspacePath.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "workspacePath is required"
            ));
        }

        try {
            cursorService.registerProject(projectName, workspacePath);

            log.info("Registered Cursor project via API: {} -> {}", projectName, workspacePath);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "projectName", projectName,
                "workspacePath", workspacePath
            ));
        } catch (Exception e) {
            log.error("Failed to register Cursor project {}: {}", projectName, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to register project: " + e.getMessage()
            ));
        }
    }

    /**
     * Unregister a project from auto-context updates.
     * <p>
     * DELETE /api/cursor/register/{projectName}
     */
    @DeleteMapping("/register/{projectName}")
    @Operation(summary = "Unregister a Cursor project",
        description = "Removes a project from the Cursor auto-context update registry. The project's context file will no longer be automatically updated.")
    @ApiResponse(responseCode = "200", description = "Unregister request processed (success=true if was registered, success=false if was not registered)")
    public ResponseEntity<Map<String, Object>> unregisterProject(
        @Parameter(description = "Project name to unregister", required = true, example = "my-project")
        @PathVariable String projectName
    ) {
        boolean removed = cursorService.unregisterProject(projectName);

        if (removed) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Project unregistered: " + projectName
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Project was not registered: " + projectName
            ));
        }
    }

    /**
     * Get all registered projects.
     * <p>
     * GET /api/cursor/projects
     */
    @GetMapping("/projects")
    @Operation(summary = "List all registered Cursor projects",
        description = "Returns a list of all projects currently registered for Cursor auto-context updates, including their workspace paths and registration timestamps.")
    @ApiResponse(responseCode = "200", description = "Registered projects list returned")
    public ResponseEntity<Map<String, Object>> getProjects() {
        Map<String, CursorProjectEntry> projects = cursorService.getAllProjects();

        // Convert to serializable format
        List<Map<String, Object>> projectList = projects.entrySet().stream()
            .map(e -> {
                Map<String, Object> m = new HashMap<>();
                m.put("projectName", e.getKey());
                m.put("workspacePath", e.getValue().workspacePath());
                m.put("installedAt", e.getValue().installedAt());
                return m;
            })
            .toList();

        return ResponseEntity.ok(Map.of(
            "projects", projectList,
            "count", projectList.size()
        ));
    }

    /**
     * Update context file for a registered project.
     * <p>
     * POST /api/cursor/context/{projectName}
     * <p>
     * Fetches fresh context from ContextService and writes to .cursor/rules/claude-mem-context.mdc
     */
    @PostMapping("/context/{projectName}")
    @Operation(summary = "Update Cursor context file",
        description = "Generates fresh context from observations for a registered project and writes it to .cursor/rules/claude-mem-context.mdc. Returns success=false if project is not registered or no context is generated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Context update processed (success=true if written, false if failed)"),
        @ApiResponse(responseCode = "404", description = "Project is not registered"),
        @ApiResponse(responseCode = "500", description = "Failed to update context due to internal error")
    })
    public ResponseEntity<Map<String, Object>> updateContext(
        @Parameter(description = "Registered project name", required = true, example = "my-project")
        @PathVariable String projectName
    ) {
        CursorProjectEntry entry = cursorService.getProject(projectName);

        if (entry == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Project not registered: " + projectName
            ));
        }

        try {
            // Generate context
            String context = contextService.generateContext(projectName);

            if (context == null || context.isBlank()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "No context generated for project: " + projectName
                ));
            }

            // Write to Cursor rules file
            boolean written = cursorService.writeContextFile(entry.workspacePath(), context);

            if (written) {
                log.info("Updated Cursor context for project: {}", projectName);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "projectName", projectName,
                    "workspacePath", entry.workspacePath()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Failed to write context file"
                ));
            }
        } catch (Exception e) {
            log.error("Failed to update Cursor context for project {}: {}", projectName, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to update context: " + e.getMessage()
            ));
        }
    }

    /**
     * Update context file for a project with custom context.
     * <p>
     * POST /api/cursor/context/{projectName}/custom
     * Body: { "context": "..." }
     */
    @PostMapping("/context/{projectName}/custom")
    @Operation(summary = "Write custom context to Cursor file",
        description = "Writes a custom context string directly to the .cursor/rules/claude-mem-context.mdc file for a registered project. Useful for providing manually curated context that differs from auto-generated observations.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Context written successfully"),
        @ApiResponse(responseCode = "400", description = "Missing required field: context"),
        @ApiResponse(responseCode = "404", description = "Project is not registered"),
        @ApiResponse(responseCode = "500", description = "Failed to write context file due to internal error")
    })
    public ResponseEntity<Map<String, Object>> updateContextCustom(
        @Parameter(description = "Registered project name", required = true, example = "my-project")
        @PathVariable String projectName,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Request body with 'context' field containing the custom context string to write", required = true)
        @org.springframework.web.bind.annotation.RequestBody Map<String, String> request
    ) {
        String context = request.get("context");

        if (context == null || context.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "context is required"
            ));
        }

        CursorProjectEntry entry = cursorService.getProject(projectName);

        if (entry == null) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Project not registered: " + projectName
            ));
        }

        try {
            boolean written = cursorService.writeContextFile(entry.workspacePath(), context);

            if (written) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "projectName", projectName
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", "Failed to write context file"
                ));
            }
        } catch (Exception e) {
            log.error("Failed to write custom context for project {}: {}", projectName, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "Failed to write context: " + e.getMessage()
            ));
        }
    }

    /**
     * Check if a project is registered.
     * <p>
     * GET /api/cursor/register/{projectName}
     */
    @GetMapping("/register/{projectName}")
    @Operation(summary = "Check if a project is registered",
        description = "Checks whether a project is currently registered for Cursor auto-context updates. Returns registered=true with details if registered, registered=false if not.")
    @ApiResponse(responseCode = "200", description = "Registration status returned")
    public ResponseEntity<Map<String, Object>> checkRegistered(
        @Parameter(description = "Project name to check", required = true, example = "my-project")
        @PathVariable String projectName
    ) {
        CursorProjectEntry entry = cursorService.getProject(projectName);

        if (entry == null) {
            return ResponseEntity.ok(Map.of(
                "registered", false,
                "projectName", projectName
            ));
        }

        return ResponseEntity.ok(Map.of(
            "registered", true,
            "projectName", projectName,
            "workspacePath", entry.workspacePath(),
            "installedAt", entry.installedAt()
        ));
    }
}
