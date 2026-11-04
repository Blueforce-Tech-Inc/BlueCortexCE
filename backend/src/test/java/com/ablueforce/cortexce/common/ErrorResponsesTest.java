package com.ablueforce.cortexce.common;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L1 — the one wire shape every error response uses. */
class ErrorResponsesTest {

    @Test
    void bodyCarriesCodeMessageAndCompatibilityMirror() {
        Map<String, Object> body = ErrorResponses.body(ErrorResponses.NOT_FOUND, "Resource not found");
        Map<?, ?> error = (Map<?, ?>) body.get("error");
        assertEquals("not_found", error.get("code"));
        assertEquals("Resource not found", error.get("message"));
        assertFalse(error.containsKey("details"));
        assertEquals("Resource not found", body.get("message"));
    }

    @Test
    void detailsAreOptionalAndStatusToCodeMappingIsExplicit() {
        Map<String, Object> body = ErrorResponses.body(
            ErrorResponses.RATE_LIMITED, "Too many requests", Map.of("retry_after", 12));
        assertEquals(Map.of("retry_after", 12), ((Map<?, ?>) body.get("error")).get("details"));
        assertEquals("unauthorized", ErrorResponses.codeForStatus(401));
        assertEquals("forbidden", ErrorResponses.codeForStatus(403));
        assertEquals("invalid_request", ErrorResponses.codeForStatus(415));
        assertEquals("payload_too_large", ErrorResponses.codeForStatus(413));
        assertEquals("rate_limited", ErrorResponses.codeForStatus(429));
        assertEquals("internal_error", ErrorResponses.codeForStatus(500));
        assertEquals("service_unavailable", ErrorResponses.codeForStatus(503));
        assertTrue(((Map<?, ?>) body.get("error")).containsKey("code"));
    }
}
