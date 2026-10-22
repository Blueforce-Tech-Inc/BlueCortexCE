package com.ablueforce.cortexce.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * The logical range in which memories are retained, searched and grouped.
 *
 * <p>A scope is <b>not</b> an authorisation boundary and <b>not</b> a tenant boundary
 * (plan §4.3 item 8); it is an ownership and query partition. First release has exactly
 * two kinds:
 *
 * <ul>
 *   <li>{@code project} — keyed by the full, exact, unmodified {@code project_path}
 *       string. No {@code realpath}, no case folding, no separator rewriting, no
 *       trailing-separator removal, no {@code Path.normalize()}. Two different stored
 *       strings ("/repo/app" and "/repo/./app") are two scopes <em>by design</em>,
 *       because that is what the existing read paths already do. Unifying those
 *       semantics would silently change what search and context return.</li>
 *   <li>{@code manual_default} — a single fixed internal key for manual memories that
 *       were saved without a project.</li>
 * </ul>
 *
 * <p>The wire contract keeps exposing {@code project}/{@code project_path} as the
 * caller-facing selector; {@code memory_scope_id} stays internal (plan §1, "Scope UUID
 * 的 wire 边界"). This record therefore never appears in an API response.
 */
public final class MemoryScopeKey {

    public static final String TYPE_PROJECT = "project";
    public static final String TYPE_MANUAL_DEFAULT = "manual_default";

    /** Fixed internal key of the single manual-default scope. Never derived from user input. */
    public static final String MANUAL_DEFAULT_KEY = "manual-default-v1";

    /** The legacy sentinel project path that manual memories without a project keep on the wire. */
    public static final String LEGACY_MANUAL_PROJECT_PATH = "manual-memories";

    private final String scopeType;
    private final String scopeKey;
    private final byte[] sha256;

    private MemoryScopeKey(String scopeType, String scopeKey, byte[] sha256) {
        this.scopeType = scopeType;
        this.scopeKey = scopeKey;
        this.sha256 = sha256;
    }

    /**
     * Produces the 32-byte digest stored alongside a scope key.
     *
     * <p>Injectable so tests can force a genuine digest collision and prove the resolver
     * refuses to merge two distinct keys. Production always uses
     * {@link #sha256Digester()}.
     */
    @FunctionalInterface
    public interface KeyDigester {
        byte[] digest(String scopeKey);
    }

    /** The only digester production code should use. */
    public static KeyDigester sha256Digester() {
        return MemoryScopeKey::sha256;
    }

    public static MemoryScopeKey manualDefault() {
        return manualDefault(sha256Digester());
    }

    public static MemoryScopeKey manualDefault(KeyDigester digester) {
        return new MemoryScopeKey(TYPE_MANUAL_DEFAULT, MANUAL_DEFAULT_KEY, digester.digest(MANUAL_DEFAULT_KEY));
    }

    /**
     * Project scope for an exact stored project path.
     *
     * @param exactProjectPath the value the caller's existing query predicate would have
     *                         used, unmodified; must be non-null and not blank
     * @throws InvalidMemoryScopeException if the path is null, empty or whitespace-only
     */
    public static MemoryScopeKey project(String exactProjectPath) {
        return project(exactProjectPath, sha256Digester());
    }

    public static MemoryScopeKey project(String exactProjectPath, KeyDigester digester) {
        if (exactProjectPath == null) {
            throw new InvalidMemoryScopeException("project scope requires a non-null project_path");
        }
        if (exactProjectPath.isBlank()) {
            // String.isBlank() is the same rule the manual-marker classifier uses for
            // "no meaningful content", so the two stay consistent.
            throw new InvalidMemoryScopeException("project scope requires a non-blank project_path");
        }
        return new MemoryScopeKey(TYPE_PROJECT, exactProjectPath, digester.digest(exactProjectPath));
    }

    public String scopeType() {
        return scopeType;
    }

    public String scopeKey() {
        return scopeKey;
    }

    public boolean isProjectScope() {
        return TYPE_PROJECT.equals(scopeType);
    }

    public boolean isManualDefault() {
        return TYPE_MANUAL_DEFAULT.equals(scopeType);
    }

    /**
     * SHA-256 of the key's UTF-8 bytes, as a defensive copy.
     *
     * <p>This digest exists only to give the uniqueness index a fixed-width column. It is
     * <b>not</b> the identity: a digest hit must still be confirmed by comparing the full
     * key under {@code COLLATE "C"} (see {@code MemoryScopeService}). Trusting the digest
     * alone would let two different paths with a (vanishingly unlikely) hash collision
     * share one scope, which would merge two projects' memories.
     */
    public byte[] sha256() {
        return sha256.clone();
    }

    /** Constant-time-equivalent structural comparison of the digest, for repository lookups. */
    public boolean sameDigest(byte[] other) {
        return other != null && MessageDigest.isEqual(this.sha256, other);
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JCA specification; absence means a broken JRE.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryScopeKey other)) return false;
        return scopeType.equals(other.scopeType) && Arrays.equals(sha256, other.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeType, Arrays.hashCode(sha256));
    }

    @Override
    public String toString() {
        // Never print the raw project path at INFO/WARN: paths are identifying.
        return "MemoryScopeKey[" + scopeType + ", sha256=" + java.util.HexFormat.of().formatHex(sha256) + "]";
    }

    public static final class InvalidMemoryScopeException extends IllegalArgumentException {
        public InvalidMemoryScopeException(String message) {
            super(message);
        }
    }
}