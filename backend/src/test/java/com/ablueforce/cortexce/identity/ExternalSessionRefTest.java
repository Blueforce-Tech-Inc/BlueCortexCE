package com.ablueforce.cortexce.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the external conversation identity (plan §1): opaque, case-sensitive ids,
 * composite uniqueness, code-point length limits.
 */
class ExternalSessionRefTest {

    @Test
    void canonicalPlatformIsNormalisedButIdIsUntouched() {
        ExternalSessionRef ref = ExternalSessionRef.of("  CODEX  ", "  Mixed Case Id / with spaces  ");
        assertEquals("codex", ref.platformSource());
        // The id keeps its spaces, its slash, its case: all are legal opaque characters.
        assertEquals("  Mixed Case Id / with spaces  ", ref.contentSessionId());
    }

    @Test
    @DisplayName("ids differing only by case are different conversations")
    void caseSensitiveIds() {
        ExternalSessionRef a = ExternalSessionRef.of("claude", "ABC");
        ExternalSessionRef b = ExternalSessionRef.of("claude", "abc");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("same id on different platforms is a different conversation")
    void platformIsPartOfIdentity() {
        ExternalSessionRef a = ExternalSessionRef.of("claude", "shared");
        ExternalSessionRef b = ExternalSessionRef.of("codex", "shared");
        assertNotEquals(a, b);
        assertEquals(a.contentSessionId(), b.contentSessionId());
    }

    @Test
    void legacyRequestDefaultsToClaudeOnly() {
        assertEquals("claude", ExternalSessionRef.ofLegacyRequest("sess-1").platformSource());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",                       // empty
        "/",                      // slash-only ids are legal characters...
    })
    void rejectsOnlyStructurallyImpossibleIds(String value) {
        if (value.isEmpty()) {
            assertThrows(ExternalSessionRef.InvalidExternalSessionRefException.class,
                () -> ExternalSessionRef.of("claude", value));
        } else {
            // A slash is a perfectly good opaque character; it only has to stay out of URL
            // path segments, which is a transport concern, not an identity concern.
            assertEquals("/", ExternalSessionRef.of("claude", value).contentSessionId());
        }
    }

    @Test
    void rejectsNull() {
        assertThrows(ExternalSessionRef.InvalidExternalSessionRefException.class,
            () -> ExternalSessionRef.of("claude", null));
    }

    @Test
    void rejectsNulCharacterWhichPostgresCannotStore() {
        assertThrows(ExternalSessionRef.InvalidExternalSessionRefException.class,
            () -> ExternalSessionRef.of("claude", "abc\u0000def"));
    }

    @Test
    @DisplayName("length is counted in code points, matching varchar(255) characters")
    void lengthIsCodePointsNotUtf16Units() {
        // 255 code points of a 4-byte character: 1020 UTF-8 bytes, 510 Java chars.
        String astral = "😀"; // U+1F600, one code point, two Java chars
        String exactly255 = astral.repeat(255);
        assertEquals(255, exactly255.codePointCount(0, exactly255.length()));
        assertEquals(510, exactly255.length());
        // Must be accepted: 255 characters fits varchar(255) even though >= 255 UTF-16 units.
        assertEquals(exactly255, ExternalSessionRef.of("claude", exactly255).contentSessionId());

        String tooLong = astral.repeat(256);
        assertThrows(ExternalSessionRef.InvalidExternalSessionRefException.class,
            () -> ExternalSessionRef.of("claude", tooLong));
    }

    @Test
    void asciiLengthBoundary() {
        String ok = "x".repeat(255);
        assertEquals(ok, ExternalSessionRef.of("claude", ok).contentSessionId());
        assertThrows(ExternalSessionRef.InvalidExternalSessionRefException.class,
            () -> ExternalSessionRef.of("claude", "x".repeat(256)));
    }

    @Test
    void invalidPlatformIsRejectedNotDefaulted() {
        assertThrows(PlatformSource.InvalidPlatformSourceException.class,
            () -> ExternalSessionRef.of("Claude Code", "abc"));
    }

    @Test
    void logKeyIsUnambiguousWithinACanonicalPlatformSet() {
        // A canonical slug never contains '|', so the debug form cannot be spoofed.
        assertTrue(ExternalSessionRef.of("claude", "a|b").toString()
            .startsWith("ExternalSessionRef[claude|"));
    }
}