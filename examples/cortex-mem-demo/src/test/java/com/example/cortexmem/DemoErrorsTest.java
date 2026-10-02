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
