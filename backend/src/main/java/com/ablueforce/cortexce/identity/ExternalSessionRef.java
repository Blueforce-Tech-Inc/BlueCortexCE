package com.ablueforce.cortexce.identity;

import java.util.Objects;

/**
 * External identity of one conversation: {@code (platform_source, content_session_id)}.
 *
 * <p>Frozen contract (plan §1):
 *
 * <ul>
 *   <li>{@code platform_source} is a {@link PlatformSource} canonical slug.</li>
 *   <li>{@code content_session_id} is an <em>opaque, case-sensitive</em> host value. It is
 *       stored and compared byte-for-byte: no {@code trim}, no lowercase, no Unicode
 *       normalisation, and never derived from a project path. Two ids are the same
 *       conversation only when the platform is the same and the strings are equal
 *       character for character.</li>
 *   <li>Length is bounded by {@link #MAX_CONTENT_SESSION_ID_LENGTH} <em>code points</em>,
 *       matching PostgreSQL {@code varchar(255)} character semantics — not UTF-8 bytes
 *       and not Java UTF-16 units.</li>
 * </ul>
 *
 * <p>This record replaces "the content session id is the session" as an assumption
 * throughout the backend. The internal relationship key is
 * {@code mem_sessions.id} (a UUID); this type is only the externally visible handle.
 */
public record ExternalSessionRef(String platformSource, String contentSessionId) {

    /** Mirrors {@code mem_sessions.content_session_id VARCHAR(255)}. */
    public static final int MAX_CONTENT_SESSION_ID_LENGTH = 255;

    public ExternalSessionRef {
        Objects.requireNonNull(platformSource, "platformSource must not be null");
        Objects.requireNonNull(contentSessionId, "contentSessionId must not be null");
        if (!PlatformSource.isValidSlug(platformSource)) {
            throw new InvalidExternalSessionRefException(
                "platformSource must be a canonical slug: " + PlatformSource.SLUG_PATTERN_TEXT);
        }
        validateContentSessionId(contentSessionId);
    }

    /**
     * Build from a raw request pair, normalising the platform and validating the id.
     *
     * @throws PlatformSource.InvalidPlatformSourceException if the platform cannot be canonicalised
     * @throws InvalidExternalSessionRefException            if the id is empty or too long
     */
    public static ExternalSessionRef of(String rawPlatformSource, String rawContentSessionId) {
        return new ExternalSessionRef(
            PlatformSource.normalize(rawPlatformSource),
            requireContentSessionId(rawContentSessionId));
    }

    /**
     * Legacy compatibility entry: absent platform defaults to Claude, which is the only
     * safe interpretation for callers written before multi-platform support. Callers that
     * support more than one platform must send it explicitly.
     */
    public static ExternalSessionRef ofLegacyRequest(String rawContentSessionId) {
        return new ExternalSessionRef(PlatformSource.DEFAULT_SOURCE, requireContentSessionId(rawContentSessionId));
    }

    private static String requireContentSessionId(String value) {
        if (value == null) {
            throw new InvalidExternalSessionRefException("contentSessionId must not be null");
        }
        validateContentSessionId(value);
        return value;
    }

    /**
     * Reject values PostgreSQL cannot store or that cannot be an identity.
     *
     * <p>Only structural impossibilities are rejected here. A leading or trailing space,
     * a slash, a percent sign or a non-ASCII letter are all <em>legal</em> opaque ids and
     * must pass through unchanged — rejecting them would silently drop real sessions.
     */
    public static void validateContentSessionId(String value) {
        if (value.isEmpty()) {
            throw new InvalidExternalSessionRefException("contentSessionId must not be empty");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new InvalidExternalSessionRefException(
                "contentSessionId must not contain U+0000 (unsupported by PostgreSQL text)");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > MAX_CONTENT_SESSION_ID_LENGTH) {
            throw new InvalidExternalSessionRefException(
                "contentSessionId exceeds " + MAX_CONTENT_SESSION_ID_LENGTH + " characters (code points)");
        }
    }

    /** Canonical {@code platform/id} form used for logs and deterministic map keys. */
    public String logKey() {
        return platformSource + "/" + contentSessionId;
    }

    @Override
    public String toString() {
        // Ids may contain anything; keep the debug form unambiguous via a delimiter
        // that a canonical platform slug can never contain.
        return "ExternalSessionRef[" + platformSource + '|' + contentSessionId + ']';
    }

    public static final class InvalidExternalSessionRefException extends IllegalArgumentException {
        public InvalidExternalSessionRefException(String message) {
            super(message);
        }
    }
}