package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * LLM service for chat completions.
 *
 * <p>Supports flexible combination of LLM providers (OpenAI, Anthropic, etc.)
 * through Spring AI's ChatClient abstraction.</p>
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final Optional<ChatClient> chatClient;

    public LlmService(List<ChatClient> chatClients) {
        // Find first available chat client (prefer auto-configured one)
        this.chatClient = chatClients.stream()
            .filter(c -> {
                // Check if it's a placeholder by class name or behavior
                String className = c.getClass().getSimpleName();
                return !className.contains("Placeholder");
            })
            .findFirst();

        if (chatClient.isEmpty()) {
            log.warn("No ChatClient configured - AI features disabled");
        } else {
            log.info("LlmService initialized with: {}", chatClient.get().getClass().getSimpleName());
        }
    }

    public String chatCompletion(String systemPrompt, String userPrompt) {
        return chatCompletionWithUsage(systemPrompt, userPrompt).content();
    }

    public record LlmResponse(String content, int totalTokens) {
        public static LlmResponse empty() {
            return new LlmResponse("", 0);
        }
    }

    public LlmResponse chatCompletionWithUsage(String systemPrompt, String userPrompt) {
        ChatClient chatClient = this.chatClient.orElseThrow(() ->
            new IllegalStateException("AI not configured. " +
                "Set OPENAI_API_KEY environment variable, or configure spring.ai.openai in application.yml, " +
                "or provide a custom ChatClient bean."));

        try {
            ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();

            if (response == null) {
                log.error("LLM response is null");
                return LlmResponse.empty();
            }

            String content = response.getResult() != null && response.getResult().getOutput() != null
                ? response.getResult().getOutput().getText()
                : "";

            int totalTokens = 0;
            if (response.getMetadata() != null) {
                Usage usage = response.getMetadata().getUsage();
                if (usage != null) {
                    totalTokens = usage.getTotalTokens();
                    log.debug("LLM usage: total={}, prompt={}, completion={}",
                        totalTokens, usage.getPromptTokens(), usage.getCompletionTokens());
                }
            }

            if (totalTokens == 0) {
                log.warn("Unable to get token usage from LLM response, usage will be estimated");
            }

            return new LlmResponse(content, totalTokens);
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return LlmResponse.empty();
        }
    }

    public boolean isAvailable() {
        return chatClient.isPresent();
    }
}
