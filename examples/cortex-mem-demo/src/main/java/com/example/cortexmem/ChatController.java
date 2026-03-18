package com.example.cortexmem;

import com.ablueforce.cortexce.ai.advisor.CortexMemoryAdvisor;
import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.client.CortexMemClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Memory-augmented chat — supports ?project= and ?conversationId=.
 *
 * <p>User prompts are auto-captured to memory when cortex.mem.capture-enabled=true.
 * Session ID resolution (in order):
 * <ol>
 *   <li>?conversationId= — Spring AI ChatMemory.CONVERSATION_ID (aligns with MessageChatMemoryAdvisor)</li>
 *   <li>CortexSessionContext — when wrapping with begin/end</li>
 * </ol>
 *
 * <p>?project= may be a demo.projects key (e.g. project-a) or absolute path.
 */
@RestController
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;
    private final CortexMemoryAdvisor defaultAdvisor;
    private final CortexMemClient cortexClient;
    private final DemoProperties demoProperties;

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
            @RequestParam(required = false) String conversationId) {
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

        if (conversationId != null && !conversationId.isBlank()) {
            return chatClientBuilder
                .defaultSystem("You are a helpful coding assistant with access to relevant past experiences.")
                .defaultAdvisors(advisor)
                .build()
                .prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, effectiveConvId))
                .user(message)
                .call()
                .content();
        }

        CortexSessionContext.begin(effectiveConvId, projectPath);
        try {
            CortexSessionContext.incrementAndGetPromptNumber();
            return chatClientBuilder
                .defaultSystem("You are a helpful coding assistant with access to relevant past experiences.")
                .defaultAdvisors(advisor)
                .build()
                .prompt()
                .user(message)
                .call()
                .content();
        } finally {
            CortexSessionContext.end();
        }
    }
}
