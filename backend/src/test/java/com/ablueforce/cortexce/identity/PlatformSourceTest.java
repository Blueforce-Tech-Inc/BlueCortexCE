package com.ablueforce.cortexce.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the frozen platform-source contract (plan §1). These are the rules that SQL
 * CHECK constraints in V20/V21 mirror, so a change here is a schema-compatibility
 * change, not a refactor.
 */
class PlatformSourceTest {

    @Nested
    @DisplayName("normalisation: exactly ASCII-space trim + ASCII case folding")
    class Normalisation {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            // explicit values pass through
            "claude,        claude",
            "codex,         codex",
            "antigravity-cli, antigravity-cli",
            "opencode,      opencode",
            // ASCII case folding
            "Claude,        claude",
            "CODEX,         codex",
            "OpEnCoDe,      opencode",
            // ASCII space trimming only
            "'  cursor  ',  cursor",
            "' claude',     claude",
        })
        void canonicalisesValidInputs(String raw, String expected) {
            assertEquals(expected, PlatformSource.normalize(raw));
        }

        @Test
        @DisplayName("missing or blank-after-trim falls back to the Claude default")
        void defaultsWhenAbsent() {
            assertEquals("claude", PlatformSource.normalize(null));
            assertEquals("claude", PlatformSource.normalize(""));
            assertEquals("claude", PlatformSource.normalize("   "));
            // tabs/newlines are NOT stripped; the result is not a valid slug
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalize("\tclaude\n"));
        }

        @Test
        @DisplayName("a no-break space is not ASCII space and must not be trimmed")
        void nonBreakingSpaceIsNotTrimmed() {
            // U+00A0 would be stripped by String.trim()'s "<= ' '" rule.
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalize(" claude"));
        }

        @Test
        @DisplayName("Unicode case folding must not apply")
        void unicodeCaseFoldingDoesNotApply() {
            // 'İ' (U+0130) lowercases to "i̇" under many locales but is not ASCII.
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalize("İstanbul"));
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalize("STRASSEß"));
        }

        @Test
        @DisplayName("Turkish locale must not change the result")
        void turkishLocaleDoesNotChangeResult() {
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                // With toLowerCase(tr) this would become "claud e"-style breakage; ASCII
                // folding is locale independent.
                assertEquals("claude", PlatformSource.normalize("CLAUDE"));
                assertEquals("cursor", PlatformSource.normalize("CURSOR"));
            } finally {
                Locale.setDefault(previous);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "has space",      // inner space
            "with/slash",
            "with:colon",
            "-leading-hyphen", // must start alphanumeric
            ".leading-dot",
            "_leading-underscore",
            "Upper-Inner",     // inner uppercase is not folded after the fold step... it is, but
            "café",       // non-ASCII
            "cafés",
            "claude\u0000",    // NUL
            "toolongtoolongtoolongtoolongtoolongtoolongtoolongtoolongtoolong", // > 50
        })
        void rejectsEverythingElse(String raw) {
            // "Upper-Inner" folds to "upper-inner" and is actually valid; assert explicitly below.
            if (raw.equals("Upper-Inner")) {
                assertEquals("upper-inner", PlatformSource.normalize(raw));
                return;
            }
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalize(raw));
        }

        @Test
        @DisplayName("50 characters is the boundary (varchar(50))")
        void lengthBoundary() {
            String fifty = "a".repeat(50);
            assertEquals(fifty, PlatformSource.normalize(fifty));
            String fiftyOne = "a".repeat(51);
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalize(fiftyOne));
        }
    }

    @Nested
    @DisplayName("stored values are not silently defaulted")
    class Stored {

        @Test
        void nullStaysNullSoAbsenceIsDistinguishable() {
            assertNull(PlatformSource.normalizeStored(null));
            assertEquals("claude", PlatformSource.normalizeStored(""));
            assertEquals("claude", PlatformSource.normalizeStored("   "));
        }

        @Test
        void invalidStoredValueThrowsRatherThanBeingRewritten() {
            assertThrows(PlatformSource.InvalidPlatformSourceException.class,
                () -> PlatformSource.normalizeStored("Claude Code"));
        }

        @Test
        void sameCanonicalComparesAfterNormalisation() {
            assertTrue(PlatformSource.sameCanonical("Codex", "codex"));
            assertFalse(PlatformSource.sameCanonical("claude", "codex"));
            assertTrue(PlatformSource.sameCanonical(null, "claude"),
                "NULL and blank both mean the compatibility default");
        }
    }

    @Test
    @DisplayName("slug validator agrees with normalisation")
    void validatorAgreesWithNormaliser() {
        assertTrue(PlatformSource.isValidSlug("claude"));
        assertTrue(PlatformSource.isValidSlug("a"));
        assertTrue(PlatformSource.isValidSlug("0abc"));
        assertFalse(PlatformSource.isValidSlug("Claude"));
        assertFalse(PlatformSource.isValidSlug(""));
        assertFalse(PlatformSource.isValidSlug(null));
        assertFalse(PlatformSource.isValidSlug("a b"));
        assertNotEquals("claudf", PlatformSource.normalize("Claude"));
    }
}