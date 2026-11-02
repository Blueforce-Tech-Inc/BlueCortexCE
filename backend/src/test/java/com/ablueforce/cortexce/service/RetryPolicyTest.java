package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.service.LlmService.FailureKind;
import com.ablueforce.cortexce.service.LlmService.ProviderFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One classification for both paths that can fail a queue job: the job itself
 * ({@link AgentService}) and the listener that catches anything escaping it.
 * Before this existed, the two used different rules and a transient error surfaced
 * from the listener as an immediate dead-letter.
 */
class RetryPolicyTest {

    @Test
    @DisplayName("classified provider failures keep their own transience")
    void providerFailuresUseTheirKind() {
        assertThat(RetryPolicy.isRetryable(new ProviderFailureException(FailureKind.RATE_LIMIT, "429")))
            .isTrue();
        assertThat(RetryPolicy.isRetryable(new ProviderFailureException(FailureKind.TIMEOUT, "read")))
            .isTrue();
        assertThat(RetryPolicy.isRetryable(new ProviderFailureException(FailureKind.UNAVAILABLE, "503")))
            .isTrue();
        assertThat(RetryPolicy.isRetryable(new ProviderFailureException(FailureKind.AUTHENTICATION, "401")))
            .isFalse();
        assertThat(RetryPolicy.isRetryable(new ProviderFailureException(FailureKind.INVALID_RESPONSE, "empty")))
            .isFalse();
    }

    @Test
    @DisplayName("auth text is terminal even when the exception is generic")
    void authTextIsNeverRetried() {
        assertThat(RetryPolicy.isRetryable(new RuntimeException("Gemini API error: 401 Unauthorized")))
            .isFalse();
        assertThat(RetryPolicy.isRetryable(new RuntimeException("API_KEY_INVALID")))
            .isFalse();
        assertThat(RetryPolicy.isRetryable(new RuntimeException("PERMISSION_DENIED")))
            .isFalse();
    }

    @Test
    @DisplayName("transport failures are transient")
    void transportFailuresAreRetryable() {
        assertThat(RetryPolicy.isRetryable(new SocketTimeoutException("read timed out"))).isTrue();
        assertThat(RetryPolicy.isRetryable(new ConnectException("refused"))).isTrue();
        assertThat(RetryPolicy.isRetryable(new TimeoutException("lock"))).isTrue();
        assertThat(RetryPolicy.isRetryable(new RuntimeException("LLM call failed"))).isTrue();
    }

    @Test
    @DisplayName("unclassified errors are terminal")
    void unclassifiedErrorsAreTerminal() {
        assertThat(RetryPolicy.isRetryable(new IllegalStateException("no text"))).isFalse();
        assertThat(RetryPolicy.isRetryable(null)).isFalse();
    }

    @Test
    @DisplayName("failure kind is a short classification, never message text")
    void failureKindNeverLeaksMessageText() {
        assertThat(RetryPolicy.failureKind(new ProviderFailureException(FailureKind.RATE_LIMIT, "429")))
            .isEqualTo("RATE_LIMIT");
        assertThat(RetryPolicy.failureKind(new SocketTimeoutException("read timed out"))).isEqualTo("TIMEOUT");
        assertThat(RetryPolicy.failureKind(new ConnectException("refused"))).isEqualTo("CONNECT");
        assertThat(RetryPolicy.failureKind(new IllegalArgumentException("bad payload"))).isEqualTo("UNKNOWN");
        assertThat(RetryPolicy.failureKind(null)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("classification only returns known tokens")
    void failureKindIsBoundedVocabulary() {
        for (Throwable error : new Throwable[]{
            new RuntimeException("prompt text: secret"),
            new ProviderFailureException(FailureKind.INVALID_RESPONSE, "empty body"),
            new java.io.IOException("headers contained api-key")
        }) {
            String kind = RetryPolicy.failureKind(error);
            assertThat(kind).isIn(
                "AUTHENTICATION", "RATE_LIMIT", "TIMEOUT", "UNAVAILABLE",
                "INVALID_RESPONSE", "UNKNOWN", "CONNECT", "VALIDATION");
            assertThat(kind).doesNotContain("prompt", "secret", "body");
        }
    }
}
