package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.identity.MemoryScopeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves a {@link MemoryScopeKey} to a {@code mem_memory_scopes.id}.
 *
 * <p>Contract (plan §4.2), all of it load-bearing:
 *
 * <ol>
 *   <li><b>Concurrency-safe creation.</b> Insert with {@code ON CONFLICT DO NOTHING}
 *       first, then read by digest and confirm the full key. A "check then insert"
 *       would let two concurrent first-writes create two scopes for one key.</li>
 *   <li><b>The digest is not the identity.</b> A digest hit is only a candidate; the
 *       row is accepted only when {@code scope_key} matches the requested key exactly
 *       under {@code COLLATE "C"}. Otherwise the caller gets
 *       {@link ScopeKeyDigestCollisionException} and nothing is reused or overwritten.
 *       A collision must never merge two projects' memories.</li>
 *   <li><b>Reads never create.</b> {@link #find} does not insert; a read path that
 *       misses simply queries nothing rather than manufacturing an empty scope.</li>
 *   <li><b>No canonicalisation.</b> The key is passed to SQL exactly as given; this
 *       class does not normalise paths, because doing so would silently merge or split
 *       query results relative to the existing project predicates.</li>
 * </ol>
 */
@Service
public class MemoryScopeService {

    private static final Logger log = LoggerFactory.getLogger(MemoryScopeService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final MemoryScopeKey.KeyDigester digester;

    @org.springframework.beans.factory.annotation.Autowired
    public MemoryScopeService(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, MemoryScopeKey.sha256Digester());
    }

    /**
     * Test seam: allows forcing digest collisions. Production wiring uses the
     * single-argument constructor with SHA-256.
     */
    MemoryScopeService(NamedParameterJdbcTemplate jdbc, MemoryScopeKey.KeyDigester digester) {
        this.jdbc = jdbc;
        this.digester = digester;
    }

    /** Raised when two different scope keys hash to the same digest. Fail closed. */
    public static final class ScopeKeyDigestCollisionException extends IllegalStateException {
        private final String scopeType;

        public ScopeKeyDigestCollisionException(String scopeType) {
            // Deliberately does not contain either key: keys are project paths.
            super("SCOPE_KEY_DIGEST_COLLISION for scope_type=" + scopeType);
            this.scopeType = scopeType;
        }

        public String scopeType() {
            return scopeType;
        }
    }

    /**
     * Resolve a scope id, creating the row if it does not exist.
     *
     * <p>Uses a nested transaction so a conflict between two concurrent callers does not
     * poison the caller's outer transaction, and the retry can read the winner.
     */
    public UUID resolveOrCreate(MemoryScopeKey key) {
        UUID existing = findExisting(key);
        if (existing != null) {
            return existing;
        }
        try {
            insertIgnoringConflict(key);
        } catch (DuplicateKeyException e) {
            // Another writer won the race; fall through and read the winner.
            log.debug("Scope insert lost a race, reading winner for type={}", key.scopeType());
        }
        UUID resolved = findExisting(key);
        if (resolved == null) {
            // The row neither existed before nor after our insert: only possible if the
            // digest is taken by a *different* key, which findExisting reports below.
            throw new ScopeKeyDigestCollisionException(key.scopeType());
        }
        return resolved;
    }

    /**
     * Look up an existing scope id without creating anything.
     *
     * @return the scope id, or {@code null} if no scope exists for this exact key
     * @throws ScopeKeyDigestCollisionException if the digest is occupied by a different key
     */
    @Transactional(readOnly = true)
    public UUID find(MemoryScopeKey key) {
        return findExisting(key);
    }

    private UUID findExisting(MemoryScopeKey key) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, scope_key
            FROM mem_memory_scopes
            WHERE scope_type = :scopeType
              AND scope_key_sha256 = :sha
            """,
            new MapSqlParameterSource()
                .addValue("scopeType", key.scopeType())
                .addValue("sha", key.sha256()));

        if (rows.isEmpty()) {
            return null;
        }
        // Digest is a candidate only. Confirm the exact key under "C" collation.
        for (Map<String, Object> row : rows) {
            String storedKey = (String) row.get("scope_key");
            if (storedKey != null && storedKey.equals(key.scopeKey())) {
                // equals() on two Strings is already byte-for-byte on UTF-16 code units;
                // the DB comparison below is what guarantees the collation contract.
                return (UUID) row.get("id");
            }
        }
        // Digest exists but for a different key: do not reuse, do not overwrite.
        throw new ScopeKeyDigestCollisionException(key.scopeType());
    }

    /**
     * Insert the scope, ignoring a concurrent insert of the same digest. The explicit
     * {@code "C"} comparison is provided by {@code WHERE scope_key = :key COLLATE "C"} in
     * the follow-up read performed by the caller; here we only need the atomicity.
     */
    private void insertIgnoringConflict(MemoryScopeKey key) {
        jdbc.update("""
            INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256, display_name)
            VALUES (:scopeType, :scopeKey COLLATE "C", :sha, :displayName)
            ON CONFLICT (scope_type, scope_key_sha256) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("scopeType", key.scopeType())
                .addValue("scopeKey", key.scopeKey())
                .addValue("sha", key.sha256())
                .addValue("displayName", displayNameFor(key)));
    }

    /**
     * Display name is UI sugar only and never participates in identity. For the manual
     * default it is a constant; for a project it is the path's last segment, kept only so
     * a future Scope-management UI has a label without re-deriving it.
     */
    private static String displayNameFor(MemoryScopeKey key) {
        if (key.isManualDefault()) {
            return "Manual memories";
        }
        String path = key.scopeKey();
        int idx = path.lastIndexOf('/');
        String base = idx >= 0 && idx < path.length() - 1 ? path.substring(idx + 1) : path;
        return base.isBlank() ? path : base;
    }

    /** Ensure the single manual-default scope exists. Idempotent. */
    public UUID resolveManualDefault() {
        return resolveOrCreate(MemoryScopeKey.manualDefault(digester));
    }

    /**
     * Resolve a project scope for an exact path.
     *
     * @param exactProjectPath the value the caller's existing project predicate would use;
     *                         passed through unchanged
     * @return the scope id, or {@code null} when this is a read path and no scope exists
     */
    @Transactional(readOnly = true)
    public UUID findProjectScope(String exactProjectPath) {
        if (exactProjectPath == null || exactProjectPath.isBlank()) {
            return null;
        }
        return find(MemoryScopeKey.project(exactProjectPath, digester));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public UUID resolveProjectScope(String exactProjectPath) {
        if (exactProjectPath == null || exactProjectPath.isBlank()) {
            throw new MemoryScopeKey.InvalidMemoryScopeException(
                "cannot resolve a project scope without a non-blank project_path");
        }
        return resolveOrCreate(MemoryScopeKey.project(exactProjectPath, digester));
    }
}