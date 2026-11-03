package com.ablueforce.cortexce.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Anthropic-compatible provider returns the answer as several content blocks and
 * Spring AI maps one generation per block. Reading only the first generation misses the
 * answer when a reasoning block comes first, which is one of the ways a 2xx response
 * produced no usable content in round 12.
 */
class ChatResponseTextTest {

    @Test
    @DisplayName("a text-only response returns its text")
    void textOnly() {
        ChatResponse response = response(generation("answer", Map.of()));
        assertThat(ChatResponseText.extract(response)).isEqualTo("answer");
    }

    @Test
    @DisplayName("a thinking block before the answer does not hide the answer")
    void thinkingBeforeAnswer() {
        ChatResponse response = response(
                generation("reasoning", Map.of("signature", "sig")),
                generation("answer", Map.of()));
        assertThat(ChatResponseText.extract(response)).isEqualTo("answer");
    }

    @Test
    @DisplayName("multiple text blocks are joined in order")
    void multipleTextBlocks() {
        ChatResponse response = response(
                generation("part one ", Map.of()),
                generation("part two", Map.of()));
        assertThat(ChatResponseText.extract(response)).isEqualTo("part one part two");
    }

    @Test
    @DisplayName("a reasoning-only response returns the reasoning text for diagnosis")
    void reasoningOnly() {
        ChatResponse response = response(generation("reasoning", Map.of("signature", "sig")));
        assertThat(ChatResponseText.extract(response)).isEqualTo("reasoning");
    }

    @Test
    @DisplayName("a redacted thinking block is not treated as the answer")
    void redactedThinkingIsNotAnswer() {
        ChatResponse response = response(generation("redacted", Map.of("data", "opaque")));
        assertThat(ChatResponseText.extract(response)).isEqualTo("redacted");
    }

    @Test
    @DisplayName("an empty or null response yields an empty string")
    void missingResponse() {
        assertThat(ChatResponseText.extract(null)).isEmpty();
    }

    private static ChatResponse response(Generation... generations) {
        List<Generation> list = List.of(generations);
        ChatResponse response = new ChatResponse(list);
        return response;
    }

    private static Generation generation(String text, Map<String, Object> metadata) {
        return new Generation(AssistantMessage.builder()
                .content(text)
                .properties(metadata)
                .build(), null);
    }
}
