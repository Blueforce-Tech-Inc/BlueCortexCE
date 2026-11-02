package com.ablueforce.cortexce.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Removes credentials and raw model output from strings that are about to be logged
 * or attached to an exception.
 *
 * <p>The provider response body is useful for diagnosis and is therefore kept, but
 * only after the parts that look like secrets are masked. Nothing here tries to redact
 * prompts: those are the user's own text and are not logged at all.
 */
public final class LogSanitizer {

    /**
     * Well-known secret shapes: sk-..., api_key=..., "authorization": "Bearer ...".
     *
     * <p>The keyword alternative allows an optional quote before the separator, so the
     * JSON form {@code "authorization": "Bearer <token>"} is masked as a whole instead of
     * leaking the token after the keyword's value.
     */
    private static final Pattern SECRET = Pattern.compile(
        "(?i)("
            + "sk-[A-Za-z0-9_-]{8,}"
            + "|(?:api[_-]?key|apikey|authorization|access[_-]?token|secret)"
            + "\\s*\"?\\s*(?:=|:)\\s*\"?(?:bearer\\s+)?[^\\s\",}]{4,}"
            + "|bearer\\s+[A-Za-z0-9._~+/-]{8,}"
        + ")");

    private LogSanitizer() {
    }

    /**
     * @return the input with every secret-looking run replaced by its name plus ***
     */
    public static String maskSecrets(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return SECRET.matcher(text).replaceAll(match -> {
            String value = match.group();
            int separator = Math.max(value.indexOf('='), value.indexOf(':'));
            if (separator > 0) {
                int end = separator + 1;
                while (end < value.length() && Character.isWhitespace(value.charAt(end))) {
                    end++;
                }
                return value.substring(0, end) + "***";
            }
            if (value.regionMatches(true, 0, "bearer", 0, 6)) {
                return "Bearer ***";
            }
            return "***";
        });
    }

    /**
     * Collapse whitespace, mask secrets, and bound the length.
     * Used for exception details that end up in logs and queue rows.
     */
    public static String safeDetail(String message, int maxLength) {
        if (message == null) {
            return "no message";
        }
        String singleLine = maskSecrets(message.replaceAll("\\s+", " ").trim());
        return singleLine.length() <= maxLength ? singleLine : singleLine.substring(0, maxLength) + "...";
    }

    /**
     * Stable, non-reversible identifier for a payload that must never be logged verbatim,
     * such as the raw model answer: the first 12 hex characters of SHA-256.
     *
     * <p>Twelve hex characters are enough to correlate two log lines that refer to the
     * same answer, while keeping the written value useless as a transcript.
     */
    public static String fingerprint(String text) {
        if (text == null || text.isEmpty()) {
            return "empty";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every Java runtime.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
