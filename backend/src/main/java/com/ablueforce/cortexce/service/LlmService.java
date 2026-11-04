package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.CortexMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Chat completion that surfaces provider failures to the caller instead of
     * swallowing them.
     *
     * <p>{@link #chatCompletionWithUsage} catches every provider exception and
     * returns {@link LlmResponse#empty()} — an empty string with zero tokens.
     * That is the right behaviour for the callers that want a degraded result
     * (they each check the content themselves), but it is wrong for a
     * <em>diagnostic</em> caller: an empty string is indistinguishable from a
     * successful call, so a connectivity probe would report success while the
     * provider is returning 401/500.</p>
     *
     * <p>This method propagates the original exception so the caller can report
     * the real cause. It does not change the behaviour of
     * {@link #chatCompletion} or {@link #chatCompletionWithUsage}.</p>
     */
    public String chatCompletionReportingFailure(String systemPrompt, String userPrompt) {
        ChatClient chatClient = this.chatClient.orElseThrow(() ->
            new IllegalStateException("AI not configured. " +
                "Set OPENAI_API_KEY environment variable, or configure spring.ai.openai in application.yml, " +
                "or provide a custom ChatClient bean."));

        ChatResponse response = chatClient.prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .chatResponse();

        if (response == null) {
            throw new IllegalStateException("LLM returned a null response");
        }

        return ChatResponseText.extract(response);
    }

    /**
     * Why a provider call produced no usable answer.
     *
     * <p>Callers that must decide between "retry" and "give up" cannot do that from an
     * empty string: a 429 and a 401 both used to look like "". Keeping the kind on the
     * response lets the observation and summary workers apply one policy — bounded retry
     * for transient failures, immediate terminal state for permanent ones — without
     * changing what {@link #chatCompletion} returns.</p>
     */
    public enum FailureKind {
        /** 401/403, invalid or expired key. Retrying cannot fix it. */
        AUTHENTICATION,
        /** 429 / quota. Transient. */
        RATE_LIMIT,
        /** Read/connect timeout. Transient. */
        TIMEOUT,
        /** 5xx, connection refused/reset. Transient. */
        UNAVAILABLE,
        /** A 2xx answer with no usable content. Repeating the same prompt cannot help. */
        INVALID_RESPONSE,
        /** Anything the classifier does not recognise. Treated as permanent. */
        UNKNOWN;

        public boolean isTransient() {
            return this == RATE_LIMIT || this == TIMEOUT || this == UNAVAILABLE;
        }
    }

    /**
     * Response envelope. {@code failure} is {@code null} on success and carries the
     * classified provider failure otherwise. The two-argument constructor is kept so
     * existing constructions remain source-compatible.
     */
    public record LlmResponse(String content, int totalTokens, FailureKind failure,
                              String failureDetail) {

        public LlmResponse(String content, int totalTokens) {
            this(content, totalTokens, null, null);
        }

        public boolean failed() {
            return failure != null;
        }

        public static LlmResponse empty() {
            return new LlmResponse("", 0);
        }
    }

    /**
     * Raised by callers that require a usable answer. Carries the classified kind so
     * failure handling can distinguish a 401 from a 429 without re-parsing messages.
     */
    public static final class ProviderFailureException extends RuntimeException {

        private final FailureKind kind;

        public ProviderFailureException(FailureKind kind, String detail) {
            super(kind + ": " + (detail == null ? "no detail" : detail));
            this.kind = kind;
        }

        public FailureKind kind() {
            return kind;
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
                log.error("LLM response is null (provider returned no response)");
                return failure(FailureKind.INVALID_RESPONSE, "null ChatResponse");
            }

            String content = ChatResponseText.extract(response);

            if (content == null || content.isBlank()) {
                log.error("LLM response contains no usable content (blank answer)");
                return failure(FailureKind.INVALID_RESPONSE, "blank content");
            }

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

            CortexMetrics.llmSuccess();
            return new LlmResponse(content, totalTokens);
        } catch (Exception e) {
            FailureKind kind = classify(e);
            log.error("LLM call failed (kind={}): {}: {}",
                kind, e.getClass().getSimpleName(), abbreviate(e.getMessage()));
            return failure(kind, e.getClass().getSimpleName() + ": " + abbreviate(e.getMessage()));
        }
    }

    private static LlmResponse failure(FailureKind kind, String detail) {
        CortexMetrics.llmFailure(kind.name());
        return new LlmResponse("", 0, kind, detail);
    }

    /**
     * Classifies a provider exception by walking the cause chain, so a wrapper added by
     * the HTTP layer does not hide the status code underneath.
     */
    public static FailureKind classify(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Set<String> classNames = new LinkedHashSet<>();
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append(' ');
            }
            classNames.add(current.getClass().getSimpleName());
            if (current instanceof java.net.SocketTimeoutException
                || current instanceof java.net.http.HttpTimeoutException
                || current instanceof java.util.concurrent.TimeoutException) {
                return FailureKind.TIMEOUT;
            }
            if (current instanceof java.net.ConnectException) {
                return FailureKind.UNAVAILABLE;
            }
            current = current.getCause();
        }

        String text = messages.toString().toLowerCase(Locale.ROOT);
        if (containsAny(text, "401", "403", "unauthorized", "forbidden",
            "invalid api key", "api_key_invalid", "permission_denied", "authentication")) {
            return FailureKind.AUTHENTICATION;
        }
        if (containsAny(text, "429", "rate limit", "rate_limit", "too many requests",
            "quota exceeded")) {
            return FailureKind.RATE_LIMIT;
        }
        if (containsAny(text, "timed out", "timeout")) {
            return FailureKind.TIMEOUT;
        }
        if (containsAny(text, "500", "502", "503", "504", "service unavailable",
            "connection refused", "connection reset")) {
            return FailureKind.UNAVAILABLE;
        }
        if (classNames.contains("TransientAiException")) {
            return FailureKind.UNAVAILABLE;
        }
        return FailureKind.UNKNOWN;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String abbreviate(String message) {
        // Provider error bodies can echo request headers. LogSanitizer keeps the
        // diagnostic value while masking anything that looks like a credential.
        return com.ablueforce.cortexce.util.LogSanitizer.safeDetail(message, 160);
    }

    public boolean isAvailable() {
        return chatClient.isPresent();
    }

    /**
     * Structured output completion using Spring AI converters.
     * For Map output: uses MapOutputConverter with outputSchema in prompt.
     * For POJO output: uses BeanOutputConverter with auto-derived schema.
     *
     * @param systemPrompt system instruction
     * @param userPrompt user content with observations
     * @param outputType target class (Map.class or POJO class)
     * @return parsed structured result
     */
    @SuppressWarnings("unchecked")
    public <T> T chatCompletionStructured(String systemPrompt, String userPrompt, Class<T> outputType) {
        ChatClient chatClient = this.chatClient.orElseThrow(() ->
            new IllegalStateException("AI not configured."));

        String response = null;
        try {
            if (Map.class.isAssignableFrom(outputType)) {
                MapOutputConverter converter = new MapOutputConverter();
                response = chatClient.prompt()
                    .system(systemPrompt + "\n\n" + converter.getFormat())
                    .user(userPrompt)
                    .call()
                    .content();
                return (T) converter.convert(response);
            } else {
                BeanOutputConverter<T> converter = new BeanOutputConverter<>(outputType);
                response = chatClient.prompt()
                    .system(systemPrompt + "\n\n" + converter.getFormat())
                    .user(userPrompt)
                    .call()
                    .content();
                return converter.convert(response);
            }
        } catch (IllegalStateException e) {
            // Re-throw config errors (e.g., "AI not configured")
            throw e;
        } catch (Exception e) {
            log.error("Structured LLM call failed: response_present={}, response_chars={}, error_type={}",
                response != null, response == null ? 0 : response.length(), e.getClass().getSimpleName());
            throw new IllegalStateException(
                "Structured LLM call failed (" + e.getClass().getSimpleName() + ")");
        }
    }
}
