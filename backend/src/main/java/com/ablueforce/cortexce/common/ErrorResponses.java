package com.ablueforce.cortexce.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One wire shape for every error the API emits.
 *
 * <p>Before round 18 three shapes coexisted: a free-text {@code {"error": "..."}} map
 * built by handlers, Spring's default {@code {timestamp, status, error, path}} body for
 * framework failures, and a handful of endpoint-specific maps. Clients had to probe the
 * status code and guess which of those they had received.
 *
 * <p>The unified body is:
 *
 * <pre>{@code
 * {
 *   "error": { "code": "invalid_request", "message": "...", "details": {...} },
 *   "message": "..."            // compatibility mirror, see below
 * }
 * }</pre>
 *
 * <p>{@code error.code} is a stable machine-readable token; {@code error.message} is the
 * human-readable explanation; {@code error.details} is optional structured context
 * (for example {@code {"retry_after": "12"}}). The top-level {@code message} repeats the
 * message because the bundled Viewer UI reads a string field named {@code message}
 * ({@code webui/src/ui/viewer/utils/save-error.ts}) and must keep working unchanged.
 *
 * <p>HTTP status codes retain their usual meaning: 400 invalid request, 401 missing or
 * invalid credentials, 403 authenticated but not allowed, 404 not found, 409 conflict,
 * 429 rate limited, 500 internal error.
 */
public final class ErrorResponses {

    public static final String INVALID_REQUEST = "invalid_request";
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String FORBIDDEN = "forbidden";
    public static final String NOT_FOUND = "not_found";
    public static final String CONFLICT = "conflict";
    public static final String RATE_LIMITED = "rate_limited";
    public static final String INTERNAL_ERROR = "internal_error";

    private ErrorResponses() {
    }

    public static Map<String, Object> body(String code, String message) {
        return body(code, message, null);
    }

    public static Map<String, Object> body(String code, String message, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) {
            error.put("details", details);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("message", message);
        return payload;
    }

    /** Code for an HTTP status, used by framework-level failures. */
    public static String codeForStatus(int status) {
        return switch (status) {
            case 400 -> INVALID_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 409 -> CONFLICT;
            case 429 -> RATE_LIMITED;
            default -> status >= 400 && status < 500 ? INVALID_REQUEST : INTERNAL_ERROR;
        };
    }
}
