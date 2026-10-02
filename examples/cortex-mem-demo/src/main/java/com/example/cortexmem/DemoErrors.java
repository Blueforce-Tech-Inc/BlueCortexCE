package com.example.cortexmem;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;

/**
 * Shared error-mapping helpers for the demo controllers.
 *
 * <p>The SDK wraps every failure in a generic {@code RuntimeException(operation + " failed")}
 * and keeps the real cause, so the backend's own status code and message are only reachable
 * by walking the cause chain. Without that walk a backend 404 surfaced here as a 500 with the
 * useless text "submitFeedback failed" — a status code that lies about what happened, and a
 * message that discards the backend's explanation.
 *
 * <p>Controllers use {@link #statusOf} / {@link #messageOf} so a not-found stays a not-found
 * and the caller still learns why. The other three demos already do this: the Go demo maps
 * NotFound to 404, and the Python and JS demos pass the backend's status straight through.
 */
final class DemoErrors {

    private DemoErrors() {
    }

    /**
     * @return the first HTTP status carried anywhere in the cause chain, or {@code null}
     *         when the failure did not come from an HTTP error response
     */
    static HttpStatusCode statusOf(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response) {
                return response.getStatusCode();
            }
        }
        return null;
    }

    static boolean isNotFound(Throwable failure) {
        HttpStatusCode status = statusOf(failure);
        return status != null && status.value() == 404;
    }

    /**
     * Extracts the backend's human-readable reason, preferring the {@code error} field of its
     * JSON body and falling back to the HTTP status line. Returns {@code null} when no HTTP
     * error response is in the chain, so callers can fall back to the SDK's generic message.
     */
    static String messageOf(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response) {
                String fromBody = errorFieldOf(response);
                if (fromBody != null) {
                    return fromBody;
                }
                return response.getStatusText()
                        + " (HTTP " + response.getStatusCode().value() + ")";
            }
        }
        return null;
    }

    private static String errorFieldOf(RestClientResponseException response) {
        byte[] body = response.getResponseBodyAsByteArray();
        if (body == null || body.length == 0) {
            return null;
        }
        String text = new String(body, StandardCharsets.UTF_8);
        // Bodies are small and flat here; a regex avoids pulling in a JSON dependency
        // for a demo that only needs the single "error" field.
        var matcher = java.util.regex.Pattern
                .compile("\"error\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return text.isBlank() ? null : text;
    }
}
