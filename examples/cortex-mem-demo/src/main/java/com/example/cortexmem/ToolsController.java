package com.example.cortexmem;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
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
    public String runToolWithCapture(
            @RequestParam(defaultValue = "/tmp/hello.txt") String path,
            @RequestParam(required = false) String project) {
        String sessionId = "demo-" + UUID.randomUUID();
        String projectPath = project != null && !project.isBlank()
            ? (demoProperties.resolveProjectPath(project) != null
                ? demoProperties.resolveProjectPath(project)
                : project)
            : System.getProperty("user.dir");
        CortexSessionContext.begin(sessionId, projectPath);
        try {
            String result = fileReadTool.readFile(path);
            CortexSessionContext.incrementAndGetPromptNumber();
            return "Tool result: " + result + " (captured to memory)";
        } finally {
            CortexSessionContext.end();
        }
    }
}
