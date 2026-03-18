package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import com.ablueforce.cortexce.client.CortexMemClient;
import org.springframework.ai.chat.memory.ChatMemory;
import com.ablueforce.cortexce.client.dto.ICLPromptRequest;
import com.ablueforce.cortexce.client.dto.ICLPromptResult;
import com.ablueforce.cortexce.client.dto.UserPromptRequest;
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
 * When capture is enabled, user prompts are recorded using the session ID from:
 * <ol>
 *   <li>Spring AI {@link ChatMemory#CONVERSATION_ID} in request context (when set via
 *       {@code .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, id))})</li>
 *   <li>{@link CortexSessionContext} when active (fallback)</li>
 * </ol>
 * <p>
 * Usage with Spring AI conversation ID:
 * <pre>{@code
 * chatClient.prompt()
 *     .advisors(advisor -> advisor
 *         .param(ChatMemory.CONVERSATION_ID, conversationId)
 *         .advisors(cortexMemoryAdvisor))
 *     .user("How do I fix the login bug?")
 *     .call()
 *     .content();
 * }</pre>
 * <p>
 * Or with CortexSessionContext:
 * <pre>{@code
 * CortexSessionContext.begin(sessionId, projectPath);
 * try {
 *     CortexSessionContext.incrementAndGetPromptNumber();
 *     chatClient.prompt().advisors(advisor).user("...").call().content();
 * } finally { CortexSessionContext.end(); }
 * }</pre>
 */
public class CortexMemoryAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CortexMemoryAdvisor.class);

    private final CortexMemClient cortexClient;
    private final String projectPath;
    private final int maxExperiences;
    private final boolean captureEnabled;
    private final int order;

    private CortexMemoryAdvisor(CortexMemClient cortexClient, String projectPath,
                                int maxExperiences, boolean captureEnabled, int order) {
        this.cortexClient = cortexClient;
        this.projectPath = projectPath;
        this.maxExperiences = maxExperiences;
        this.captureEnabled = captureEnabled;
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

        captureUserPromptIfActive(request, userText);

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

    private void captureUserPromptIfActive(ChatClientRequest request, String userText) {
        if (!captureEnabled) {
            return;
        }

        String sessionId = resolveSessionId(request);
        if (sessionId == null || sessionId.isBlank() || ChatMemory.DEFAULT_CONVERSATION_ID.equals(sessionId)) {
            return;
        }

        String projectPath = resolveProjectPath(request);
        int promptNumber = CortexSessionContext.isActive()
            ? Math.max(1, CortexSessionContext.getPromptNumber())
            : 1;

        try {
            cortexClient.recordUserPrompt(UserPromptRequest.builder()
                .sessionId(sessionId)
                .projectPath(projectPath)
                .promptText(userText)
                .promptNumber(promptNumber)
                .build());
            log.debug("Recorded user prompt for session {} (from Spring AI conversation id or CortexSessionContext)", sessionId);
        } catch (Exception e) {
            log.warn("Failed to record user prompt: {}", e.getMessage());
        }
    }

    private String resolveSessionId(ChatClientRequest request) {
        var ctx = request.context();
        if (ctx != null) {
            Object ctxVal = ctx.get(ChatMemory.CONVERSATION_ID);
            if (ctxVal != null && !ctxVal.toString().isBlank()) {
                return ctxVal.toString();
            }
        }
        if (CortexSessionContext.isActive()) {
            String sid = CortexSessionContext.getSessionId();
            return "unknown-session".equals(sid) ? null : sid;
        }
        return null;
    }

    private String resolveProjectPath(ChatClientRequest request) {
        if (CortexSessionContext.isActive()) {
            return CortexSessionContext.getProjectPath();
        }
        return projectPath != null ? projectPath : "";
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
        private boolean captureEnabled = true;
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

        /**
         * Enable or disable auto-capture of user prompts when CortexSessionContext is active.
         * Default is true.
         */
        public Builder captureEnabled(boolean captureEnabled) {
            this.captureEnabled = captureEnabled;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public CortexMemoryAdvisor build() {
            return new CortexMemoryAdvisor(client, projectPath, maxExperiences, captureEnabled, order);
        }
    }
}
