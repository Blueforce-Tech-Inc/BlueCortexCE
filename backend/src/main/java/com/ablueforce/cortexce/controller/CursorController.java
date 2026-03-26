package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.ContextService;
import com.ablueforce.cortexce.service.CursorService;
import com.ablueforce.cortexce.service.CursorService.CursorProjectEntry;
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
    public ResponseEntity<Map<String, Object>> registerProject(
        @RequestBody Map<String, String> request
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
    public ResponseEntity<Map<String, Object>> unregisterProject(
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
    public ResponseEntity<Map<String, Object>> updateContext(
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
    public ResponseEntity<Map<String, Object>> updateContextCustom(
        @PathVariable String projectName,
        @RequestBody Map<String, String> request
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
    public ResponseEntity<Map<String, Object>> checkRegistered(
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
