package com.ablueforce.cortexce.service;

import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Extracts the assistant text from a chat response.
 *
 * <p>An Anthropic-compatible provider returns the answer as several content blocks,
 * and Spring AI materializes one {@link Generation} per block. Reasoning models can
 * put a {@code thinking} block before the answer; {@link ChatResponse#getResult()}
 * returns only the first block, so reading it yields reasoning text (or an empty
 * string for a redacted block) instead of the answer. The block carries a
 * {@code signature} (thinking) or {@code data} (redacted thinking) property, which is
 * how this helper tells it apart from a text block.</p>
 *
 * <p>Text blocks are concatenated in order. When the response has no text block at
 * all, the helper returns the last reasoning text as a fallback so that callers can
 * record what the provider actually sent; callers that require an answer still
 * classify it as invalid.</p>
 */
public final class ChatResponseText {

    /** Metadata keys that mark a block as reasoning rather than answer text. */
    private static final String[] REASONING_KEYS = {"signature", "data"};

    private ChatResponseText() {
    }

    public static String extract(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        String reasoningFallback = "";
        for (Generation generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null) {
                continue;
            }
            AssistantMessage message = generation.getOutput();
            String value = message.getText();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (isReasoning(message.getMetadata())) {
                reasoningFallback = value;
                continue;
            }
            text.append(value);
        }
        return text.length() > 0 ? text.toString() : reasoningFallback;
    }

    private static boolean isReasoning(Map<String, Object> metadata) {
        if (metadata == null) {
            return false;
        }
        for (String key : REASONING_KEYS) {
            if (metadata.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
