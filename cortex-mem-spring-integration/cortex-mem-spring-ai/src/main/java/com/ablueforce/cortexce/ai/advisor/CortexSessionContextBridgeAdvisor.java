package com.ablueforce.cortexce.ai.advisor;

import com.ablueforce.cortexce.ai.context.CortexSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * Bridge Advisor that automatically activates {@link CortexSessionContext} when
 * {@link ChatMemory#CONVERSATION_ID} is present in the request context.
 * <p>
 * This enables @Tool auto-capture (via {@link com.ablueforce.cortexce.ai.aspect.CortexToolAspect})
 * in pure ChatClient scenarios without manual {@code CortexSessionContext.begin/end} calls.
 * <p>
 * Usage: Add this advisor before {@link CortexMemoryAdvisor} in the chain:
 * <pre>{@code
 * chatClient.prompt()
 *     .advisors(spec -> spec
 *         .param(ChatMemory.CONVERSATION_ID, conversationId)
 *         .advisors(cortexSessionContextBridgeAdvisor, cortexMemoryAdvisor))
 *     .user("How do I fix the login bug?")
 *     .call()
 *     .content();
 * }</pre>
 * <p>
 * When CONVERSATION_ID is set, this advisor will:
 * <ol>
 *   <li>Call {@code CortexSessionContext.begin(conversationId, projectPath)} before the chain</li>
 *   <li>Increment prompt number for capture ordering</li>
 *   <li>Call {@code CortexSessionContext.end()} in finally after the chain completes</li>
 * </ol>
 * <p>
 * <b>Do not nest this advisor inside a manual {@code CortexSessionContext.begin}/{@code end}
 * scope.</b> The two are alternatives, not layers: this advisor's {@code begin} overwrites
 * whatever context is already on the thread, and its {@code end} then removes it, so an
 * outer scope does not survive the call. Measured on the 2026-10-04 review: with
 * {@code begin("outer-session", "/outer/project")} already active, one {@code adviseCall}
 * left {@code isActive() == false}, {@code getSessionId() == "unknown-session"} and an empty
 * project path — silently, with no error. Any later {@code @Tool} call in that outer scope
 * then records against those fallback values. The Java demo keeps the two paths strictly
 * separate for this reason: a request carrying {@code CONVERSATION_ID} goes through this
 * advisor and never calls {@code begin} manually, while a request without one calls
 * {@code begin} manually and this advisor passes straight through. Tracked as P2-44 —
 * making it nest-safe needs either a save/restore on {@link CortexSessionContext} (new
 * public surface) or a decision about which context should win when the two differ.
 *
 * @see CortexMemoryAdvisor
 * @see com.ablueforce.cortexce.ai.aspect.CortexToolAspect
 */
public class CortexSessionContextBridgeAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CortexSessionContextBridgeAdvisor.class);

    private final String projectPath;
    private final int order;

    private CortexSessionContextBridgeAdvisor(String projectPath, int order) {
        this.projectPath = projectPath != null ? projectPath : "";
        this.order = order;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String conversationId = resolveConversationId(request);
        if (conversationId == null || conversationId.isBlank()) {
            return chain.nextCall(request);
        }

        log.debug("Bridge: activating CortexSessionContext for conversation {}", conversationId);
        CortexSessionContext.begin(conversationId, projectPath);
        CortexSessionContext.incrementAndGetPromptNumber();

        try {
            return chain.nextCall(request);
        } finally {
            CortexSessionContext.end();
            log.debug("Bridge: released CortexSessionContext for conversation {}", conversationId);
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        String conversationId = resolveConversationId(request);
        if (conversationId == null || conversationId.isBlank()) {
            return chain.nextStream(request);
        }

        log.debug("Bridge: activating CortexSessionContext for conversation {}", conversationId);
        CortexSessionContext.begin(conversationId, projectPath);
        CortexSessionContext.incrementAndGetPromptNumber();

        try {
            Flux<ChatClientResponse> flux = chain.nextStream(request);
            return flux.doFinally(signal -> {
                CortexSessionContext.end();
                log.debug("Bridge: released CortexSessionContext for conversation {}", conversationId);
            });
        } catch (Exception e) {
            CortexSessionContext.end();
            throw e;
        }
    }

    @Override
    public String getName() {
        return "CortexSessionContextBridgeAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    private String resolveConversationId(ChatClientRequest request) {
        var ctx = request.context();
        if (ctx == null) {
            return null;
        }
        Object val = ctx.get(ChatMemory.CONVERSATION_ID);
        if (val == null || val.toString().isBlank()) {
            return null;
        }
        String id = val.toString();
        if (ChatMemory.DEFAULT_CONVERSATION_ID.equals(id)) {
            return null;
        }
        return id;
    }

    public static class Builder {
        private String projectPath = "";
        private int order = -100;

        public Builder projectPath(String projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        /**
         * Order for advisor chain. Default -100 so this runs before CortexMemoryAdvisor (order 0).
         */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public CortexSessionContextBridgeAdvisor build() {
            return new CortexSessionContextBridgeAdvisor(projectPath, order);
        }
    }
}
