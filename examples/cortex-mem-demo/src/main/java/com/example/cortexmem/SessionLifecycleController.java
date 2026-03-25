package com.example.cortexmem;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.ai.observation.ObservationCaptureService;
import com.ablueforce.cortexce.client.dto.SessionEndRequest;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Full session lifecycle demo: start → prompt → tool → end.
 *
 * <p>Shows memory isolation by Session/Conversation: each conversation has a
 * distinct session_id; memory within the same project is organized by session.
 *
 * <p>Project isolation: specify via project param; memory is stored and retrieved by project_path.
 */
@RestController
@RequestMapping("/demo/session")
public class SessionLifecycleController {

    private final SessionStartClient sessionStartClient;
    private final ObservationCaptureService captureService;
    private final FileReadTool fileReadTool;
    private final DemoProperties demoProperties;

    public SessionLifecycleController(SessionStartClient sessionStartClient,
                                      ObservationCaptureService captureService,
                                      FileReadTool fileReadTool,
                                      DemoProperties demoProperties) {
        this.sessionStartClient = sessionStartClient;
        this.captureService = captureService;
        this.fileReadTool = fileReadTool;
        this.demoProperties = demoProperties;
    }

    /**
     * 1. Start session — calls backend session/start, links session to project.
     *
     * @param project Project key (e.g. project-a) or absolute path
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSession(
            @RequestParam(defaultValue = "default") String project) {
        String projectPath = demoProperties.resolveProjectPath(project);
        if (projectPath == null) projectPath = System.getProperty("user.dir");

        String sessionId = "demo-session-" + UUID.randomUUID();
        try {
            Map<String, Object> result = sessionStartClient.startSession(sessionId, projectPath);
            result.put("session_id", sessionId);
            result.put("project", project);
            result.put("project_path", projectPath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to start session: " + e.getMessage(),
                "session_id", sessionId,
                "project", project,
                "project_path", projectPath
            ));
        }
    }

    /**
     * 2. Record user prompt — must be called within CortexSessionContext (see lifecycle).
     */
    @PostMapping("/prompt")
    public ResponseEntity<String> recordPrompt(
            @RequestParam String sessionId,
            @RequestParam String projectPath,
            @RequestParam String prompt,
            @RequestParam(defaultValue = "1") int promptNumber) {
        CortexSessionContext.begin(sessionId, projectPath);
        try {
            captureService.recordUserPrompt(UserPromptRequest.builder()
                .sessionId(sessionId)
                .projectPath(projectPath)
                .promptText(prompt)
                .promptNumber(promptNumber)
                .build());
            return ResponseEntity.ok("Prompt recorded");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error: Failed to record prompt — " + e.getMessage());
        } finally {
            CortexSessionContext.end();
        }
    }

    /**
     * 3. Tool call (auto-captures observation) — must be within CortexSessionContext.
     */
    @GetMapping("/tool")
    public ResponseEntity<String> runTool(
            @RequestParam String sessionId,
            @RequestParam String projectPath,
            @RequestParam(defaultValue = "/tmp/hello.txt") String path) {
        CortexSessionContext.begin(sessionId, projectPath);
        try {
            String result = fileReadTool.readFile(path);
            CortexSessionContext.incrementAndGetPromptNumber();
            return ResponseEntity.ok("Tool result: " + result + " (captured to memory)");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error: Tool execution failed — " + e.getMessage());
        } finally {
            CortexSessionContext.end();
        }
    }

    /**
     * 4. End session — triggers summary generation.
     */
    @PostMapping("/end")
    public ResponseEntity<String> endSession(
            @RequestParam String sessionId,
            @RequestParam String projectPath,
            @RequestParam(required = false) String lastMessage) {
        try {
            captureService.recordSessionEnd(SessionEndRequest.builder()
                .sessionId(sessionId)
                .projectPath(projectPath)
                .lastAssistantMessage(lastMessage != null ? lastMessage : "")
                .build());
            return ResponseEntity.ok("Session ended: " + sessionId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error: Failed to end session — " + e.getMessage());
        }
    }

    /**
     * One-shot full lifecycle: start → prompt → tool → end.
     * Verifies all capture types.
     */
    @PostMapping("/lifecycle")
    public Map<String, Object> fullLifecycle(
            @RequestParam(defaultValue = "default") String project,
            @RequestParam(defaultValue = "How to fix a bug?") String prompt,
            @RequestParam(defaultValue = "/tmp/hello.txt") String toolPath) {

        String projectPath = demoProperties.resolveProjectPath(project);
        if (projectPath == null) projectPath = System.getProperty("user.dir");

        String sessionId = "demo-lifecycle-" + UUID.randomUUID();

        // 1. Start
        Map<String, Object> startResult;
        try {
            startResult = sessionStartClient.startSession(sessionId, projectPath);
        } catch (Exception e) {
            return Map.of(
                "error", "Failed to start session: " + e.getMessage(),
                "session_id", sessionId
            );
        }

        // 2. Prompt + 3. Tool + 4. End (within context)
        CortexSessionContext.begin(sessionId, projectPath);
        try {
            captureService.recordUserPrompt(UserPromptRequest.builder()
                .sessionId(sessionId).projectPath(projectPath)
                .promptText(prompt).promptNumber(1).build());
            String toolResult = fileReadTool.readFile(toolPath);
            CortexSessionContext.incrementAndGetPromptNumber();
            captureService.recordSessionEnd(SessionEndRequest.builder()
                .sessionId(sessionId).projectPath(projectPath)
                .lastAssistantMessage("Processed: " + toolPath).build());

            return Map.of(
                "session_id", sessionId,
                "project", project,
                "project_path", projectPath,
                "session_db_id", startResult.getOrDefault("session_db_id", ""),
                "prompt_recorded", true,
                "tool_result", toolResult,
                "session_ended", true
            );
        } finally {
            CortexSessionContext.end();
        }
    }
}
