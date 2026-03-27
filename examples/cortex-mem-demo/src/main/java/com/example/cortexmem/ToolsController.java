package com.example.cortexmem;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Demo controller that invokes @Tool-annotated methods for memory capture.
 *
 * <p>The tool must be in a separate bean so Spring AOP intercepts it.
 * Self-invocation (calling this.readFile from same class) bypasses the proxy.
 */
@RestController
public class ToolsController {

    private static final Logger log = LoggerFactory.getLogger(ToolsController.class);

    private final FileReadTool fileReadTool;
    private final DemoProperties demoProperties;

    public ToolsController(FileReadTool fileReadTool, DemoProperties demoProperties) {
        this.fileReadTool = fileReadTool;
        this.demoProperties = demoProperties;
    }

    /**
     * Tool call — auto-captured to memory. Supports ?project= to switch project.
     */
    @GetMapping("/demo/tool")
    public ResponseEntity<String> runToolWithCapture(
            @RequestParam(defaultValue = "/tmp/hello.txt") String path,
            @RequestParam(required = false) String project) {
        String sessionId = "demo-" + UUID.randomUUID();
        String projectPath = System.getProperty("user.dir");
        if (project != null && !project.isBlank()) {
            String resolved = demoProperties.resolveProjectPath(project);
            projectPath = resolved != null ? resolved : project;
        }
        CortexSessionContext.begin(sessionId, projectPath);
        try {
            String result = fileReadTool.readFile(path);
            CortexSessionContext.incrementAndGetPromptNumber();
            return ResponseEntity.ok("Tool result: " + result + " (captured to memory)");
        } catch (Exception e) {
            log.error("Tool execution failed for path={}", path, e);
            return ResponseEntity.internalServerError()
                    .body("Error: Tool execution failed — " + e.getMessage());
        } finally {
            CortexSessionContext.end();
        }
    }
}
