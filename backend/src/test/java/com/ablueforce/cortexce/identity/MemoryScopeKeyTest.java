package com.ablueforce.cortexce.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins Memory Scope identity (plan §4.2/§4.3): exact unmodified project paths, a single
 * fixed manual-default scope, and a digest that must never be trusted as identity.
 */
class MemoryScopeKeyTest {

    @Test
    @DisplayName("project scopes are keyed by the exact stored string, with no canonicalisation")
    void projectKeysAreExact() {
        String[][] distinct = {
            {"/repo/app", "/repo/./app"},
            {"/repo/app", "/repo/app/"},
            {"/repo/app", "/repo/App"},
            {"/repo/app", "/repo/app "},
            {"/repo/app", "repo/app"},
        };
        for (String[] pair : distinct) {
            assertNotEquals(
                MemoryScopeKey.project(pair[0]),
                MemoryScopeKey.project(pair[1]),
                "these must remain two scopes: " + pair[0] + " vs " + pair[1]);
        }
    }

    @Test
    void identicalStringsAreTheSameScope() {
        assertEquals(MemoryScopeKey.project("/repo/app"), MemoryScopeKey.project("/repo/app"));
        assertEquals(MemoryScopeKey.project("/repo/app").scopeType(), MemoryScopeKey.TYPE_PROJECT);
    }

    @Test
    void manualDefaultIsASingleFixedScope() {
        MemoryScopeKey a = MemoryScopeKey.manualDefault();
        MemoryScopeKey b = MemoryScopeKey.manualDefault();
        assertEquals(a, b);
        assertTrue(a.isManualDefault());
        assertEquals("manual-default-v1", a.scopeKey());
        assertEquals("manual_default", a.scopeType());
        assertFalse(a.isProjectScope());
    }

    @Test
    void projectAndManualDefaultNeverCollideEvenForTheSameString() {
        // "/manual-memories" as an explicit project is a project scope; the default scope
        // is a different type with a fixed key, so the pair cannot be confused.
        assertNotEquals(MemoryScopeKey.project("manual-default-v1"), MemoryScopeKey.manualDefault());
    }

    @Test
    void rejectsNullAndBlankProjectPaths() {
        assertThrows(MemoryScopeKey.InvalidMemoryScopeException.class, () -> MemoryScopeKey.project(null));
        assertThrows(MemoryScopeKey.InvalidMemoryScopeException.class, () -> MemoryScopeKey.project(""));
        assertThrows(MemoryScopeKey.InvalidMemoryScopeException.class, () -> MemoryScopeKey.project("   "));
        // Unicode whitespace also counts as blank, matching String.isBlank()
        assertThrows(MemoryScopeKey.InvalidMemoryScopeException.class,
            () -> MemoryScopeKey.project("　"));
    }

    @Test
    @DisplayName("non-blank paths are never trimmed")
    void doesNotTrimNonBlankPaths() {
        MemoryScopeKey padded = MemoryScopeKey.project("  /repo/app  ");
        assertEquals("  /repo/app  ", padded.scopeKey());
        assertNotEquals(MemoryScopeKey.project("/repo/app"), padded);
    }

    @Test
    void digestIsSha256OverUtf8Bytes() throws Exception {
        String key = "/repo/été";
        byte[] expected = MessageDigest.getInstance("SHA-256")
            .digest(key.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, MemoryScopeKey.project(key).sha256());
        assertEquals(32, MemoryScopeKey.project(key).sha256().length);
    }

    @Test
    @DisplayName("the digest array is not shared mutable state")
    void digestIsDefensivelyCopied() {
        MemoryScopeKey k = MemoryScopeKey.project("/repo/app");
        byte[] first = k.sha256();
        first[0] ^= 0xFF;
        assertArrayEquals(
            MessageDigestSha256("/repo/app"),
            k.sha256(),
            "mutating the returned array must not change the key");
    }

    @Test
    @DisplayName("a digest match alone is not equality once the full key differs")
    void digestAloneIsNotIdentity() {
        MemoryScopeKey a = MemoryScopeKey.project("/repo/app");
        // sameDigest only answers "same hash"; the service must still compare the raw key.
        assertTrue(a.sameDigest(MemoryScopeKey.project("/repo/app").sha256()));
        assertFalse(a.sameDigest(MemoryScopeKey.project("/repo/other").sha256()));
    }

    @Test
    void toStringNeverLeaksTheProjectPath() {
        String s = MemoryScopeKey.project("/Users/alice/secret-project").toString();
        assertFalse(s.contains("alice"), "project paths must not be logged: " + s);
        assertTrue(s.contains("sha256="));
    }

    private static byte[] MessageDigestSha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}