package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.service.LlmService.FailureKind;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Proves the open circuit is a fast fail, not a quiet retry: the provider is
 * never called, and the returned kind keeps the retry budget (so the row is not
 * lost while the provider is down).
 */
class LlmServiceCircuitBreakerTest {

    @Test
    void openCircuitSkipsTheProviderCallAndReturnsARetryableFailure() {
        ProviderCircuitBreaker breaker = new ProviderCircuitBreaker();
        ReflectionTestUtils.setField(breaker, "failureThreshold", 1);
        breaker.onOutcome(FailureKind.UNAVAILABLE);

        ChatClient chatClient = mock(ChatClient.class);
        LlmService service = new LlmService(List.of(chatClient), breaker);

        LlmService.LlmResponse response = service.chatCompletionWithUsage("system", "user");

        assertThat(response.failed()).isTrue();
        assertThat(response.failure()).isEqualTo(FailureKind.CIRCUIT_OPEN);
        assertThat(response.totalTokens()).isZero();
        verify(chatClient, never()).prompt();
        assertThat(RetryPolicy.isRetryable(
            new LlmService.ProviderFailureException(FailureKind.CIRCUIT_OPEN, "open"))).isTrue();
    }
}
