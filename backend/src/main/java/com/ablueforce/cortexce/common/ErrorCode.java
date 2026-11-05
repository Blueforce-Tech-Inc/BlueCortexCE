package com.ablueforce.cortexce.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The one registry of machine-readable {@code error.code} values.
 *
 * <p>Handlers used to spell the token at every call site
 * ({@code ErrorResponses.body("invalid_request", ...)}), which made a typo
 * indistinguishable from a new code and let the published reference advertise a
 * code the server never emitted. Every code now comes from this enum; the
 * documentation checker reads it directly, so a documented example that is not
 * registered fails the API contract check.
 *
 * <p>Add a value here, then add it to {@link ErrorResponses} if it also needs a
 * status mapping. The Java test enforces uniqueness and the snake_case shape; the
 * Python checker enforces that every documented code exists in this file.
 */
public enum ErrorCode {

    INVALID_REQUEST("invalid_request"),
    UNAUTHORIZED("unauthorized"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    CONFLICT("conflict"),
    PAYLOAD_TOO_LARGE("payload_too_large"),
    RATE_LIMITED("rate_limited"),
    INTERNAL_ERROR("internal_error"),
    SERVICE_UNAVAILABLE("service_unavailable");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** The registered code, or empty when the token is not part of the contract. */
    public static Optional<ErrorCode> fromCode(String code) {
        for (ErrorCode value : values()) {
            if (value.code.equals(code)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** All registered wire values, in declaration order. */
    public static Set<String> codes() {
        Set<String> codes = new LinkedHashSet<>();
        for (ErrorCode value : values()) {
            codes.add(value.code);
        }
        return Collections.unmodifiableSet(codes);
    }
}
