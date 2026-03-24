package com.example.cortexmem;

import com.ablueforce.cortexce.ai.advisor.CortexMemoryAdvisor;
import com.ablueforce.cortexce.ai.advisor.CortexSessionContextBridgeAdvisor;
import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.ai.tools.CortexMemoryTools;
import com.ablueforce.cortexce.client.CortexMemClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Memory-augmented chat — supports ?project=, ?conversationId=, ?useTools=.
 *
 * <p>User prompts and @Tool observations are auto-captured when cortex.mem.capture-enabled=true.
 * Session ID resolution (in order):
 * <ol>
 *   <li>?conversationId= — Spring AI ChatMemory.CONVERSATION_ID + CortexSessionContextBridgeAdvisor auto begin/end</li>
 *   <li>CortexSessionContext — when wrapping with begin/end (legacy path)</li>
 * </ol>
 *
 * <p>?project= may be a demo.projects key (e.g. project-a) or absolute path.
 *
 * <p>?useTools=true — when cortex.mem.memory-tools-enabled=true, adds CortexMemoryTools so the AI
 * can call searchMemories/getMemoryContext on demand.
 */
@RestController
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;
    private final CortexMemoryAdvisor defaultAdvisor;
    private final CortexMemClient cortexClient;
    private final DemoProperties demoProperties;

    @Autowired(required = false)
    private CortexSessionContextBridgeAdvisor contextBridgeAdvisor;

    @Autowired(required = false)
    private CortexMemoryTools memoryTools;

    public ChatController(ChatClient.Builder builder, CortexMemoryAdvisor advisor,
                          CortexMemClient cortexClient, DemoProperties demoProperties) {
        this.chatClientBuilder = builder;
        this.defaultAdvisor = advisor;
        this.cortexClient = cortexClient;
        this.demoProperties = demoProperties;
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam(defaultValue = "Hello") String message,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false, defaultValue = "false") boolean useTools) {
        CortexMemoryAdvisor advisor = defaultAdvisor;
        String projectPath = System.getProperty("user.dir");
        if (project != null && !project.isBlank()) {
            projectPath = demoProperties.resolveProjectPath(project);
            if (projectPath == null) projectPath = project;
            advisor = CortexMemoryAdvisor.builder(cortexClient)
                .projectPath(projectPath)
                .maxExperiences(4)
                .build();
        }

        String effectiveConvId = conversationId != null && !conversationId.isBlank()
            ? conversationId
            : "chat-" + UUID.randomUUID();

        var clientBuilder = chatClientBuilder
            .defaultSystem("You are a helpful coding assistant with access to relevant past experiences. " +
                (memoryTools != null && useTools ? "You can call searchMemories or getMemoryContext when you need to look up past experiences." : ""));
        var client = (contextBridgeAdvisor != null
            ? clientBuilder.defaultAdvisors(contextBridgeAdvisor, advisor)
            : clientBuilder.defaultAdvisors(advisor))
            .build();

        if (memoryTools != null && useTools) {
            client = client.mutate().defaultTools(memoryTools).build();
        }

        if (conversationId != null && !conversationId.isBlank()) {
            try {
                var spec = client.prompt()
                    .advisors(spec1 -> spec1.param(ChatMemory.CONVERSATION_ID, effectiveConvId))
                    .user(message);
                return spec.call().content();
            } catch (Exception e) {
                return "Error: Chat failed — " + e.getMessage();
            }
        }

        CortexSessionContext.begin(effectiveConvId, projectPath);
        try {
            CortexSessionContext.incrementAndGetPromptNumber();
            return client.prompt().user(message).call().content();
        } catch (Exception e) {
            return "Error: Chat failed — " + e.getMessage();
        } finally {
            CortexSessionContext.end();
        }
    }
}
