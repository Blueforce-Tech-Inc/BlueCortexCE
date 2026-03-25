package com.example.cortexmem;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Project configuration display — demonstrates cortex.mem.project-path and demo.projects.
 *
 * <p>Set default project via config file, or switch programmatically (session/start, CortexSessionContext).
 */
@RestController
public class ProjectsController {

    private final DemoProperties demoProperties;

    public ProjectsController(DemoProperties demoProperties) {
        this.demoProperties = demoProperties;
    }

    /**
     * List configured demo projects. Used for multi-project isolation demo.
     */
    @GetMapping("/demo/projects")
    public ResponseEntity<Map<String, Object>> listProjects() {
        Map<String, Object> result = new HashMap<>();
        result.put("configured_projects", demoProperties.getProjects());
        result.put("default_project", System.getProperty("user.dir"));
        result.put("usage", Map.of(
            "session_start", "POST /demo/session/start?project=<key>",
            "lifecycle", "POST /demo/session/lifecycle?project=<key>",
            "memory_query", "GET /memory/experiences?task=...&project=<path>",
            "chat", "GET /chat?message=...&project=<key>"
        ));
        return ResponseEntity.ok(result);
    }
}
