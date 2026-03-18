package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;

/**
 * Spring AI Advisor that automatically injects Cortex CE memory context
 * into the AI conversation.
 * <p>
 * For each call, it retrieves relevant historical experiences from the
 * memory system and prepends them to the system prompt as ICL context.
 * <p>
 * Usage:
 * <pre>{@code
 * var advisor = CortexMemoryAdvisor.builder(cortexClient)
 *     .projectPath("/my/project")
 *     .maxExperiences(4)
 *     .build();
 *
 * chatClient.prompt()
 *     .advisors(advisor)
 *     .user("How do I fix the login bug?")
 *     .call()
 *     .content();
 * }</pre>
 */
public class CortexMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CortexMemoryAdvisor.class);

    private final CortexMemClient cortexClient;
    private final String projectPath;
    private final int maxExperiences;
    private final int order;

    private CortexMemoryAdvisor(CortexMemClient cortexClient, String projectPath,
                                int maxExperiences, int order) {
        this.cortexClient = cortexClient;
        this.projectPath = projectPath;
        this.maxExperiences = maxExperiences;
        this.order = order;
    }

    public static Builder builder(CortexMemClient client) {
        return new Builder(client);
    }

    // ==================== CallAdvisor ====================

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientRequest enriched = enrichRequest(request);
        return chain.nextCall(enriched);
    }

    // ==================== StreamAdvisor ====================

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ChatClientRequest enriched = enrichRequest(request);
        return chain.nextStream(enriched);
    }

    // ==================== Advisor metadata ====================

    @Override
    public String getName() {
        return "CortexMemoryAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    // ==================== Internal ====================

    private ChatClientRequest enrichRequest(ChatClientRequest request) {
        String userText = extractUserText(request);
        if (userText == null || userText.isBlank()) {
            return request;
        }

        try {
            ICLPromptResult iclResult = cortexClient.buildICLPrompt(
                ICLPromptRequest.builder()
                    .task(userText)
                    .project(projectPath)
                    .build());

            if (iclResult == null || iclResult.prompt() == null || iclResult.prompt().isBlank()) {
                log.debug("No memory context found for task: {}", truncate(userText, 80));
                return request;
            }

            log.debug("Injecting {} experience(s) as memory context", iclResult.experienceCountAsInt());

            var messages = new ArrayList<>(request.prompt().getInstructions());
            messages.addFirst(new SystemMessage(iclResult.prompt()));
            Prompt augmented = new Prompt(messages, request.prompt().getOptions());

            return ChatClientRequest.builder()
                .prompt(augmented)
                .context(request.context())
                .build();
        } catch (Exception e) {
            log.warn("Failed to enrich request with memory context: {}", e.getMessage());
            return request;
        }
    }

    private String extractUserText(ChatClientRequest request) {
        var userMsg = request.prompt().getUserMessage();
        return userMsg != null ? userMsg.getText() : null;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // ==================== Builder ====================

    public static class Builder {
        private final CortexMemClient client;
        private String projectPath = "";
        private int maxExperiences = 4;
        private int order = 0;

        private Builder(CortexMemClient client) {
            this.client = client;
        }

        public Builder projectPath(String projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder maxExperiences(int maxExperiences) {
            this.maxExperiences = maxExperiences;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public CortexMemoryAdvisor build() {
            return new CortexMemoryAdvisor(client, projectPath, maxExperiences, order);
        }
    }
}
