package com.ablueforce.cortexce.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Canonical platform source slug.
 *
 * <p>Frozen contract (plan §1, "平台规范化"): a persisted {@code platform_source} is
 * a lowercase ASCII slug matching {@value #SLUG_PATTERN_TEXT}. The normalisation
 * performed at the API boundary is <em>exactly</em> two steps and nothing else:
 *
 * <ol>
 *   <li>strip leading and trailing ASCII space {@code U+0020};</li>
 *   <li>fold ASCII {@code A}–{@code Z} to lowercase.</li>
 * </ol>
 *
 * <p>Everything else is rejected rather than coerced. In particular this class does
 * <b>not</b> use {@link String#trim()} (which also strips other control characters),
 * {@link String#toLowerCase(Locale)} (which is locale- and Unicode-sensitive — Turkish
 * "I" and any non-ASCII letter would silently change the identity), or
 * Unicode-aware whitespace collapsing. Those transformations are exactly the kind of
 * "helpful" normalisation that would make the Java writer and the SQL constraint
 * disagree about the same stored value.
 *
 * <p>Host alias mapping (for example {@code transcript} → {@code codex}) is an
 * <em>adapter</em> concern and deliberately lives outside this class: the backend
 * accepts only canonical slugs so that one host cannot acquire two identities
 * depending on which client talked to it.
 */
public final class PlatformSource {

    /** Compatibility default for requests that predate the multi-platform wire format. */
    public static final String DEFAULT_SOURCE = "claude";

    /** Canonical slug shape. Kept as a string so SQL CHECK can mirror it verbatim. */
    public static final String SLUG_PATTERN_TEXT =
        "^[a-z0-9][a-z0-9._-]{0,49}$";

    /** Must stay byte-identical in meaning to the CHECK constraint emitted by V20. */
    public static final Pattern SLUG_PATTERN = Pattern.compile(SLUG_PATTERN_TEXT);

    /**
     * Maximum accepted length in <em>code points</em>, matching
     * {@code varchar(50)}'s character semantics rather than Java's UTF-16 units.
     */
    public static final int MAX_CODE_POINTS = 50;

    private PlatformSource() {
    }

    /**
     * Thrown when a caller supplies a value that cannot become a canonical slug.
     * Callers map this to HTTP 400; it must never be swallowed into a default,
     * because silently substituting {@code claude} would misattribute the record.
     */
    public static final class InvalidPlatformSourceException extends IllegalArgumentException {
        public InvalidPlatformSourceException(String message) {
            super(message);
        }
    }

    /**
     * Normalise a raw request value to its canonical slug.
     *
     * @param raw value from the request; {@code null} or blank-after-ASCII-trim yields
     *            {@link #DEFAULT_SOURCE} to keep legacy Claude callers working
     * @return a canonical slug
     * @throws InvalidPlatformSourceException if the value cannot be canonicalised
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return DEFAULT_SOURCE;
        }
        String trimmed = stripAsciiSpace(raw);
        if (trimmed.isEmpty()) {
            return DEFAULT_SOURCE;
        }
        String folded = foldAsciiUpperCase(trimmed);
        if (!isValidSlug(folded)) {
            throw new InvalidPlatformSourceException(
                "platform_source must match " + SLUG_PATTERN_TEXT
                    + " (lowercase ASCII slug, max " + MAX_CODE_POINTS + " characters)");
        }
        return folded;
    }

    /**
     * Normalise for paths that already hold a persisted value and must not invent
     * one. Unlike {@link #normalize(String)} this never substitutes the default:
     * a stored {@code NULL} stays {@code null} so callers can tell "not recorded"
     * apart from "explicitly claude".
     */
    public static String normalizeStored(String stored) {
        if (stored == null) {
            return null;
        }
        String trimmed = stripAsciiSpace(stored);
        if (trimmed.isEmpty()) {
            return DEFAULT_SOURCE;
        }
        String folded = foldAsciiUpperCase(trimmed);
        if (!isValidSlug(folded)) {
            throw new InvalidPlatformSourceException(
                "stored platform_source is not a canonical slug");
        }
        return folded;
    }

    /** True when {@code candidate} is already a canonical slug (no normalisation needed). */
    public static boolean isValidSlug(String candidate) {
        if (candidate == null) {
            return false;
        }
        // reject early on longer-than-possible inputs: codePointCount is cheap but
        // a multi-megabyte string should not even reach the regex engine.
        if (candidate.length() > MAX_CODE_POINTS * 2) {
            return false;
        }
        if (candidate.codePointCount(0, candidate.length()) > MAX_CODE_POINTS) {
            return false;
        }
        return SLUG_PATTERN.matcher(candidate).matches()
            // A required charset covering only ASCII makes this redundant, but the
            // explicit guard documents *why* no Unicode folding is needed.
            && candidate.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
    }

    /** Strip leading/trailing U+0020 only. U+00A0 and friends are not ASCII space. */
    static String stripAsciiSpace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == ' ') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(start, end);
    }

    /**
     * ASCII-only upper-to-lower folding. Deliberately not {@code toLowerCase()},
     * whose behaviour depends on default locale and Unicode case tables.
     */
    static String foldAsciiUpperCase(String value) {
        char[] chars = value.toCharArray();
        boolean changed = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= 'A' && c <= 'Z') {
                chars[i] = (char) (c + ('a' - 'A'));
                changed = true;
            }
        }
        return changed ? new String(chars) : value;
    }

    /**
     * Compare two persisted values by <em>effective</em> identity.
     *
     * <p>Unlike {@link #normalizeStored(String)}, which preserves {@code null} so callers can
     * distinguish "never recorded" from "explicitly claude", this comparison folds both onto
     * the compatibility default: a legacy row with {@code NULL} platform and a row explicitly
     * labelled {@code claude} are the same conversation source, and treating them as different
     * would manufacture a platform mismatch out of nothing.
     */
    public static boolean sameCanonical(String a, String b) {
        return Objects.equals(effectiveSource(a), effectiveSource(b));
    }

    /** Persisted value folded onto its effective identity (NULL/blank → default). */
    private static String effectiveSource(String value) {
        return value == null ? DEFAULT_SOURCE : normalizeStored(value);
    }
}