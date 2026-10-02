package com.example.cortexmem;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DemoErrorsTest {

    private static RestClientResponseException httpError(int status, String body) {
        return new RestClientResponseException(
                "GET http://127.0.0.1:37777/api/x",
                HttpStatusCode.valueOf(status),
                status >= 400 && status < 500 ? "Client Error" : "Server Error",
                org.springframework.http.HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void shouldFindStatusThroughNestedCauseChain() {
        // The SDK wraps everything: RuntimeException("submitFeedback failed") -> HttpClientErrorException
        Throwable wrapped = new RuntimeException("submitFeedback failed",
                new IllegalStateException("intermediate", httpError(404, "{\"error\":\"Observation not found\"}")));

        assertThat(DemoErrors.statusOf(wrapped)).isEqualTo(HttpStatusCode.valueOf(404));
        assertThat(DemoErrors.isNotFound(wrapped)).isTrue();
        assertThat(DemoErrors.messageOf(wrapped)).isEqualTo("Observation not found");
    }

    @Test
    void shouldNotTreatOtherStatusesAsNotFound() {
        Throwable wrapped = new RuntimeException("op failed", httpError(500, "{\"error\":\"boom\"}"));
        assertThat(DemoErrors.isNotFound(wrapped)).isFalse();
        assertThat(DemoErrors.statusOf(wrapped)).isEqualTo(HttpStatusCode.valueOf(500));
        assertThat(DemoErrors.messageOf(wrapped)).isEqualTo("boom");
    }

    @Test
    void shouldReturnNullWhenNoHttpErrorIsInTheChain() {
        Throwable plain = new RuntimeException("connection reset",
                new IllegalStateException("no http here"));
        assertThat(DemoErrors.statusOf(plain)).isNull();
        assertThat(DemoErrors.isNotFound(plain)).isFalse();
        assertThat(DemoErrors.messageOf(plain)).isNull();
    }

    @Test
    void shouldReturnClientStatusForAnyBackend4xx() {
        // The case this exists for: a malformed observation id is the backend's
        // 400, and reporting it as 500 tells the caller their typo broke the server.
        for (int code : new int[]{400, 401, 403, 404, 409, 422}) {
            Throwable wrapped = new RuntimeException("op failed", httpError(code, "{\"error\":\"nope\"}"));
            assertThat(DemoErrors.clientStatus(wrapped))
                    .as("status %d should pass through", code)
                    .isEqualTo(HttpStatusCode.valueOf(code));
        }
    }

    @Test
    void shouldNotReturnClientStatusForServerErrors() {
        // A 5xx genuinely is this demo's problem; it stays a 500.
        for (int code : new int[]{500, 502, 503}) {
            Throwable wrapped = new RuntimeException("op failed", httpError(code, "{\"error\":\"boom\"}"));
            assertThat(DemoErrors.clientStatus(wrapped))
                    .as("status %d must not be passed through", code)
                    .isNull();
        }
    }

    @Test
    void shouldReturnNullClientStatusWhenNoHttpErrorIsInTheChain() {
        Throwable plain = new RuntimeException("connection reset",
                new IllegalStateException("no http here"));
        assertThat(DemoErrors.clientStatus(plain)).isNull();
    }

    @Test
    void shouldFallBackToStatusLineWhenBodyHasNoErrorField() {
        Throwable wrapped = new RuntimeException("op failed", httpError(404, ""));
        assertThat(DemoErrors.isNotFound(wrapped)).isTrue();
        assertThat(DemoErrors.messageOf(wrapped)).contains("HTTP 404");
    }

    @Test
    void shouldUnescapeQuotesInsideTheErrorField() {
        Throwable wrapped = new RuntimeException("op failed",
                httpError(400, "{\"error\":\"bad \\\"field\\\" here\"}"));
        assertThat(DemoErrors.messageOf(wrapped)).isEqualTo("bad \"field\" here");
    }
}
