package com.ablueforce.cortexce.service;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pairs with the outage harness in scripts/provider-outage-e2e.py.
 *
 * <p>The circuit breaker treats TIMEOUT and UNAVAILABLE as "the provider answered
 * nothing", and any other kind as an answered provider that resets the streak. Before
 * this test, a JDK client I/O error ("HTTP/1.1 header parser received no bytes") was
 * classified UNKNOWN, so three genuine outages interleaved with that error kept the
 * circuit closed: the fast-fail path never stayed on for the cooldown window.</p>
 */
class LlmServiceClassificationTest {

    @Test
    @DisplayName("a read timeout is TIMEOUT")
    void socketTimeout() {
        assertThat(LlmService.classify(new SocketTimeoutException("Read timed out")))
            .isEqualTo(LlmService.FailureKind.TIMEOUT);
    }

    @Test
    @DisplayName("an IOException that only says timeout stays TIMEOUT")
    void ioTimeoutKeepsItsKind() {
        assertThat(LlmService.classify(new IOException("Connection timed out")))
            .isEqualTo(LlmService.FailureKind.TIMEOUT);
    }

    @Test
    @DisplayName("a transport-level I/O error is UNAVAILABLE, not UNKNOWN")
    void transportErrorIsUnavailable() {
        // The exact message the JDK client produced next to the 500s in the outage
        // harness; it used to classify UNKNOWN and close the circuit.
        Throwable failure = new ResourceAccessException(
            "I/O error on POST request",
            new IOException("HTTP/1.1 header parser received no bytes"));
        assertThat(LlmService.classify(failure))
            .isEqualTo(LlmService.FailureKind.UNAVAILABLE);
    }

    @Test
    @DisplayName("a wrapped transport error is still UNAVAILABLE")
    void wrappedTransportErrorIsUnavailable() {
        Throwable failure = new RuntimeException("retry exhausted",
            new IOException("Connection reset by peer"));
        assertThat(LlmService.classify(failure))
            .isEqualTo(LlmService.FailureKind.UNAVAILABLE);
    }

    @Test
    @DisplayName("a 5xx answer is UNAVAILABLE")
    void serverError() {
        assertThat(LlmService.classify(
            new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)))
            .isEqualTo(LlmService.FailureKind.UNAVAILABLE);
    }

    @Test
    @DisplayName("a 401 answer is AUTHENTICATION and must not count as unreachable")
    void unauthorized() {
        assertThat(LlmService.classify(
            new HttpClientErrorException(HttpStatus.UNAUTHORIZED)))
            .isEqualTo(LlmService.FailureKind.AUTHENTICATION);
    }

    @Test
    @DisplayName("a 429 answer is RATE_LIMIT and must not count as unreachable")
    void rateLimited() {
        assertThat(LlmService.classify(
            new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)))
            .isEqualTo(LlmService.FailureKind.RATE_LIMIT);
    }

    @Test
    @DisplayName("an unclassified failure stays UNKNOWN")
    void unknown() {
        assertThat(LlmService.classify(new RuntimeException("boom")))
            .isEqualTo(LlmService.FailureKind.UNKNOWN);
    }
}
