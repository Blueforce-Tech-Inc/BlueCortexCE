package com.example.cortexmem;

import com.ablueforce.cortexce.ai.advisor.CortexMemoryAdvisor;
import com.ablueforce.cortexce.client.CortexMemClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Memory-augmented chat — supports ?project= to switch retrieval scope.
 *
 * <p>?project= may be a demo.projects key (e.g. project-a) or absolute path.
 * When omitted, uses cortex.mem.project-path default.
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
            @RequestParam(required = false) String project) {
        CortexMemoryAdvisor advisor = defaultAdvisor;
        if (project != null && !project.isBlank()) {
            String projectPath = demoProperties.resolveProjectPath(project);
            if (projectPath == null) projectPath = project;
            advisor = CortexMemoryAdvisor.builder(cortexClient)
                .projectPath(projectPath)
                .maxExperiences(4)
                .build();
        }
        return chatClientBuilder
            .defaultSystem("You are a helpful coding assistant with access to relevant past experiences.")
            .defaultAdvisors(advisor)
            .build()
            .prompt()
            .user(message)
            .call()
            .content();
    }
}
