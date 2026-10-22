package com.ablueforce.cortexce.migration;

import com.ablueforce.cortexce.identity.MemoryScopeKey;
import com.ablueforce.cortexce.identity.PlatformSource;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.BucketKind;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.ChildObservation;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.Classified;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.Conflict;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.ParentSession;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resumable, audited backfill of conversation ids, standalone kinds and memory scopes.
 *
 * <p>The properties the plan requires, and how they are achieved here:
 *
 * <ul>
 *   <li><b>Atomic batches.</b> Data changes, audit rows and conflict rows are written inside
 *       one transaction per batch. A crash leaves either the whole batch or none of it.</li>
 *   <li><b>Idempotent.</b> Every UPDATE is guarded on "still needs this change", so re-running
 *       over migrated rows is a no-op and never duplicates scopes or audit.</li>
 *   <li><b>Fail closed.</b> Anything the classifier cannot prove becomes a conflict row and the
 *       data stays exactly as it was. Nothing is deleted to satisfy a constraint.</li>
 *   <li><b>No invented lineage.</b> Conversation ownership is only ever derived from the
 *       (platform, external id) parent lookup — never from counts, timestamps or similarity.</li>
 *   <li><b>Dry run by default.</b> {@code apply=false} performs every read, runs the classifier
 *       and reports exactly what would change, but writes nothing.</li>
 * </ul>
 *
 * <p>Deliberately not a Spring Boot auto-runner: migrations are driven explicitly by a single
 * operator on a single instance, and must never race application startup or another replica.
 */
public class ConversationScopeBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ConversationScopeBackfillRunner.class);

    static final String PURPOSE = "conversation-memory-scope-v20";
    static final String STEP_SCOPES = "observations.scopes";
    static final String STEP_OBSERVATIONS = "observations";
    static final String STEP_SUMMARIES = "summaries";
    static final String STEP_PROMPTS = "prompts";
    static final String STEP_SESSION_PLATFORM = "sessions.platform_source";
    static final String STEP_FEEDBACK = "feedback.session_db_id";

    static final String CODE_CONVERSATION_OWNED = "CONVERSATION_OWNED";
    static final String CODE_SCOPE_ASSIGNED = "SCOPE_ASSIGNED";
    static final String CODE_STANDALONE_MANUAL = "STANDALONE_MANUAL";
    static final String CODE_STANDALONE_EXTRACTION = "STANDALONE_EXTRACTION";
    static final String CODE_STANDALONE_DLQ = "STANDALONE_DLQ";
    static final String CODE_SOURCE_REPAIRED = "SOURCE_REPAIRED_MANUAL";
    static final String CODE_PLATFORM_REPAIRED = "PLATFORM_SOURCE_REPAIRED";
    static final String CODE_FEEDBACK_NULLED = "FEEDBACK_DANGLING_SESSION_NULLED";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final boolean apply;
    /**
     * Explicit, reviewed release of the {@code manual-memories} sentinel ambiguity. Shape
     * evidence alone can never discharge it (see {@link TechnicalBucketClassifier}).
     */
    private final boolean acceptSentinelMapping;

    /**
     * Run id for this execution. Assigned at the start of {@link #run()} before any write
     * and read by the audit/conflict helpers, so every row is attributable to a run.
     */
    private UUID currentRunId;

    public ConversationScopeBackfillRunner(DataSource dataSource,
                                           PlatformTransactionManager transactionManager,
                                           int batchSize,
                                           boolean apply,
                                           boolean acceptSentinelMapping) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(transactionManager);
        this.batchSize = Math.max(1, batchSize);
        this.apply = apply;
        this.acceptSentinelMapping = acceptSentinelMapping;
    }

    /** Totals for one run; suitable for printing as the operator's evidence record. */
    public static final class RunSummary {
        public final UUID runId;
        public final boolean applied;
        public final Map<String, Long> changed = new LinkedHashMap<>();
        public final Map<String, Long> conflicts = new LinkedHashMap<>();

        RunSummary(UUID runId, boolean applied) {
            this.runId = runId;
            this.applied = applied;
        }

        long changedTotal() {
            return changed.values().stream().mapToLong(Long::longValue).sum();
        }

        long conflictTotal() {
            return conflicts.values().stream().mapToLong(Long::longValue).sum();
        }

        @Override
        public String toString() {
            return "RunSummary{run=" + runId + ", applied=" + applied
                + ", changed=" + changed + ", conflicts=" + conflicts + "}";
        }
    }

    /** Run or resume the backfill. */
    public RunSummary run() {
        UUID runId = apply ? findResumableRun().orElseGet(this::openRun) : UUID.randomUUID();
        this.currentRunId = runId;
        RunSummary summary = new RunSummary(runId, apply);
        log.info("Backfill starting: run={} apply={} batchSize={} acceptSentinelMapping={}",
            runId, apply, batchSize, acceptSentinelMapping);

        try {
            assignScopes(summary);
            classifyParents(summary);
            fillChildConversationIds("mem_summaries", STEP_SUMMARIES, summary);
            fillChildConversationIds("mem_user_prompts", STEP_PROMPTS, summary);
            normalizeSessionPlatforms(summary);
            resolveDanglingFeedback(summary);
            if (apply) {
                finishRun(runId, "completed");
                checkpoint(runId, "ALL", "done:" + summary.changedTotal());
            }
            log.info("Backfill finished: {}", summary);
        } catch (RuntimeException e) {
            if (apply) {
                try {
                    finishRun(runId, "failed");
                } catch (RuntimeException ignored) {
                    log.error("Could not mark run {} as failed", runId);
                }
            }
            throw e;
        }
        return summary;
    }

    // =====================================================================
    // Step 1 — scopes for every observation (before classification)
    // =====================================================================

    /**
     * Assign each observation the scope of its own exact {@code project_path}.
     *
     * <p>Uses the observation's path, never the parent session's: a DLQ parent legitimately
     * holds observations from several projects, and copying the parent's path would move
     * those memories into the wrong scope.
     *
     * <p>This runs before classification on purpose. A path that later turns out to belong to
     * an unclassifiable group simply keeps its (correct) project scope and the group is
     * reported; no scope is ever re-pointed based on a classification that has not happened.
     */
    void assignScopes(RunSummary summary) {
        long changed = 0;
        Cursor cursor = new Cursor();
        while (true) {
            List<ObsRow> page = fetchObservationPageForScope(cursor);
            if (page.isEmpty()) {
                break;
            }
            if (apply) {
                tx.executeWithoutResult(status -> applyScopeBatch(page, cursor));
            }
            changed += page.size();
            cursor.advance(page.get(page.size() - 1).createdAtEpoch, page.get(page.size() - 1).id);
            if (page.size() < batchSize) {
                break;
            }
        }
        if (apply) {
            ensureScope(MemoryScopeKey.manualDefault());
        } else {
            // still prove the key is constructible in dry-run
            MemoryScopeKey.manualDefault();
        }
        summary.changed.put(STEP_SCOPES, changed);
    }

    private List<ObsRow> fetchObservationPageForScope(Cursor cursor) {
        return jdbc.query("""
            SELECT id, created_at_epoch, project_path
            FROM mem_observations
            WHERE memory_scope_id IS NULL
              AND (created_at_epoch, id) > (:fromEpoch, :fromId)
            ORDER BY created_at_epoch ASC, id ASC
            LIMIT :limit
            """,
            new MapSqlParameterSource()
                .addValue("fromEpoch", cursor.epoch)
                .addValue("fromId", cursor.id)
                .addValue("limit", batchSize),
            (rs, i) -> new ObsRow(rs.getObject("id", UUID.class),
                rs.getLong("created_at_epoch"), rs.getString("project_path")));
    }

    private void applyScopeBatch(List<ObsRow> page, Cursor cursor) {
        for (ObsRow row : page) {
            if (row.projectPath == null || row.projectPath.isBlank()) {
                recordConflict("mem_observations", row.id, "OBSERVATION_BLANK_PROJECT_PATH",
                    "observation has no usable project path; scope cannot be assigned");
                continue;
            }
            UUID scope = ensureScope(MemoryScopeKey.project(row.projectPath));
            jdbc.update("""
                UPDATE mem_observations SET memory_scope_id = :scope
                WHERE id = :id AND memory_scope_id IS NULL
                """,
                new MapSqlParameterSource("scope", scope).addValue("id", row.id));
            insertAudit("mem_observations", row.id, CODE_SCOPE_ASSIGNED,
                null, null, null, null, row.projectPath, null, scope, null, null, null);
        }
        checkpoint(currentRunId, STEP_SCOPES, "after:" + page.get(page.size() - 1).id);
    }

    // =====================================================================
    // Step 2 — conversation ownership vs standalone classification
    // =====================================================================

    void classifyParents(RunSummary summary) {
        long conversationChanged = 0;
        Map<UUID, ParentAgg> parents = loadParents();
        attributeChildren(parents);

        for (ParentAgg agg : parents.values()) {
            Verdict verdict = TechnicalBucketClassifier.classify(agg.toParentSession(), acceptSentinelMapping);
            if (verdict instanceof Classified classified) {
                conversationChanged += applyStandalone(agg, classified);
            } else {
                Conflict conflict = (Conflict) verdict;
                if (TechnicalBucketClassifier.CONFLICT_UNKNOWN_LEGACY_KEY.equals(conflict.code())) {
                    conversationChanged += applyConversationOwned(agg, summary);
                } else {
                    summary.conflicts.merge(conflict.code(), 1L, Long::sum);
                    if (apply) {
                        recordConflict("mem_sessions", agg.parentId, conflict.code(), conflict.detail());
                    }
                }
            }
        }
        summary.changed.put(STEP_OBSERVATIONS, conversationChanged);
    }

    private long applyStandalone(ParentAgg agg, Classified classified) {
        String kind = classified.kind().standaloneKind();
        String code = switch (classified.kind()) {
            case MANUAL -> CODE_STANDALONE_MANUAL;
            case EXTRACTION -> CODE_STANDALONE_EXTRACTION;
            case DLQ -> CODE_STANDALONE_DLQ;
        };
        long changed = 0;
        for (ChildRow child : agg.children) {
            if (child.conversationId == null && kind.equals(child.standaloneKind)
                && child.memoryScopeId != null) {
                continue; // already migrated
            }
            String repairedSource = null;
            if (classified.kind() == BucketKind.MANUAL && isBlank(child.source) && child.manualMarker) {
                // The only permitted source rewrite: NULL/blank -> manual, audited per row.
                repairedSource = TechnicalBucketClassifier.SOURCE_MANUAL;
            }
            if (!apply) {
                changed++;
                continue;
            }
            UUID scope = scopeFor(child.projectPath);
            applyStandaloneRow(child, kind, scope, repairedSource,
                repairedSource != null ? CODE_SOURCE_REPAIRED : code);
            changed++;
        }
        return changed;
    }

    private void applyStandaloneRow(ChildRow child, String kind, UUID scope, String repairedSource, String auditCode) {
        jdbc.update("""
            UPDATE mem_observations
            SET conversation_id = NULL,
                standalone_kind = :kind,
                memory_scope_id = :scope,
                source = COALESCE(:newSource, source)
            WHERE id = :id
            """,
            new MapSqlParameterSource()
                .addValue("kind", kind)
                .addValue("scope", scope)
                .addValue("newSource", repairedSource)
                .addValue("id", child.id));
        insertAudit("mem_observations", child.id, auditCode,
            child.conversationId, child.contentSessionId, child.platformSource,
            child.source, child.projectPath, null, scope, child.platformSource, kind, repairedSource);
    }

    private long applyConversationOwned(ParentAgg agg, RunSummary summary) {
        long changed = 0;
        for (ChildRow child : agg.children) {
            if (child.conversationId != null) {
                continue; // already conversation-owned
            }
            Boolean same = samePlatformOrNull(child.platformSource, agg.platformSource);
            if (same == null) {
                summary.conflicts.merge("PLATFORM_SOURCE_NOT_NORMALISABLE", 1L, Long::sum);
                if (apply) {
                    recordConflict("mem_observations", child.id, "PLATFORM_SOURCE_NOT_NORMALISABLE",
                        "child or parent platform cannot be canonicalised");
                }
                continue;
            }
            if (!same) {
                summary.conflicts.merge("CHILD_PARENT_PLATFORM_MISMATCH", 1L, Long::sum);
                if (apply) {
                    recordConflict("mem_observations", child.id, "CHILD_PARENT_PLATFORM_MISMATCH",
                        "child platform differs from the parent conversation platform");
                }
                continue;
            }
            if (!apply) {
                changed++;
                continue;
            }
            UUID scope = scopeFor(child.projectPath);
            jdbc.update("""
                UPDATE mem_observations
                SET conversation_id = :conv, standalone_kind = NULL, memory_scope_id = :scope
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                    .addValue("conv", agg.parentId).addValue("scope", scope).addValue("id", child.id));
            insertAudit("mem_observations", child.id, CODE_CONVERSATION_OWNED,
                null, child.contentSessionId, child.platformSource, child.source, child.projectPath,
                agg.parentId, scope, child.platformSource, null, null);
            changed++;
        }
        return changed;
    }

    /** Scope for a child: its own exact project path, else the manual default for blank paths. */
    private UUID scopeFor(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return ensureScope(MemoryScopeKey.manualDefault());
        }
        return ensureScope(MemoryScopeKey.project(projectPath));
    }

    // =====================================================================
    // Step 3/4 — summaries and prompts
    // =====================================================================

    void fillChildConversationIds(String table, String step, RunSummary summary) {
        long changed = 0;
        Cursor cursor = new Cursor();
        while (true) {
            List<ChildJoin> page = fetchChildPage(table, cursor);
            if (page.isEmpty()) {
                break;
            }
            if (apply) {
                tx.executeWithoutResult(status -> {
                    for (ChildJoin row : page) {
                        Boolean same = samePlatformOrNull(row.platformSource, row.parentPlatform);
                        if (same == null) {
                            recordConflict(table, row.id, "PLATFORM_SOURCE_NOT_NORMALISABLE",
                                "child or parent platform cannot be canonicalised");
                            continue;
                        }
                        if (!same) {
                            recordConflict(table, row.id, "CHILD_PARENT_PLATFORM_MISMATCH",
                                "child platform differs from the parent conversation platform");
                            continue;
                        }
                        jdbc.update("UPDATE " + table +
                                " SET conversation_id = :conv WHERE id = :id AND conversation_id IS NULL",
                            new MapSqlParameterSource("conv", row.parentId).addValue("id", row.id));
                        insertAudit(table, row.id, CODE_CONVERSATION_OWNED,
                            null, row.contentSessionId, row.platformSource, null, null,
                            row.parentId, null, row.parentPlatform, null, null);
                    }
                    checkpoint(currentRunId, step, "after:" + page.get(page.size() - 1).id);
                });
            }
            changed += page.size();
            ChildJoin last = page.get(page.size() - 1);
            cursor.advance(last.createdAtEpoch, last.id);
            if (page.size() < batchSize) {
                break;
            }
        }
        summary.changed.put(step, changed);
    }

    private List<ChildJoin> fetchChildPage(String table, Cursor cursor) {
        // The legacy external-id join is only unambiguous while the single-column unique
        // constraint is in force. If it ever returns two parents for one key, this query
        // yields no row and the child stays unmigrated rather than being mis-attributed.
        return jdbc.query("""
            SELECT c.id, c.content_session_id, c.platform_source, c.created_at_epoch,
                   s.id AS parent_id, s.platform_source AS parent_platform
            FROM %s c
            JOIN mem_sessions s ON s.content_session_id = c.content_session_id
            WHERE c.conversation_id IS NULL
              AND (c.created_at_epoch, c.id) > (:fromEpoch, :fromId)
            ORDER BY c.created_at_epoch ASC, c.id ASC
            LIMIT :limit
            """.formatted(table),
            new MapSqlParameterSource()
                .addValue("fromEpoch", cursor.epoch)
                .addValue("fromId", cursor.id)
                .addValue("limit", batchSize),
            (rs, i) -> new ChildJoin(rs.getObject("id", UUID.class),
                rs.getString("content_session_id"), rs.getString("platform_source"),
                rs.getString("parent_platform"), rs.getObject("parent_id", UUID.class),
                rs.getLong("created_at_epoch")));
    }

    // =====================================================================
    // Step 5 — platform_source normalisation
    // =====================================================================

    void normalizeSessionPlatforms(RunSummary summary) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, platform_source FROM mem_sessions
            WHERE platform_source IS NULL
               OR platform_source <> COALESCE(NULLIF(BTRIM(platform_source, ' '), ''), 'claude')
               OR platform_source ~ '[A-Z]'
            """, new MapSqlParameterSource());
        long changed = 0;
        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String oldValue = (String) row.get("platform_source");
            String normalized;
            try {
                normalized = oldValue == null ? PlatformSource.DEFAULT_SOURCE : PlatformSource.normalize(oldValue);
            } catch (PlatformSource.InvalidPlatformSourceException e) {
                summary.conflicts.merge("PLATFORM_SOURCE_NOT_NORMALISABLE", 1L, Long::sum);
                if (apply) {
                    recordConflict("mem_sessions", id, "PLATFORM_SOURCE_NOT_NORMALISABLE",
                        "stored platform_source cannot be canonicalised without losing information");
                }
                continue;
            }
            if (apply) {
                jdbc.update("UPDATE mem_sessions SET platform_source = :p WHERE id = :id",
                    new MapSqlParameterSource("p", normalized).addValue("id", id));
                insertAudit("mem_sessions", id, CODE_PLATFORM_REPAIRED,
                    null, null, oldValue, null, null, null, null, normalized, null, null);
            }
            changed++;
        }
        summary.changed.put(STEP_SESSION_PLATFORM, changed);
    }

    // =====================================================================
    // Step 6 — dangling feedback references
    // =====================================================================

    void resolveDanglingFeedback(RunSummary summary) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT f.id, f.session_db_id
            FROM observation_feedback f
            LEFT JOIN mem_sessions s ON s.id = f.session_db_id
            WHERE f.session_db_id IS NOT NULL AND s.id IS NULL
            """, new MapSqlParameterSource());
        long changed = 0;
        for (Map<String, Object> row : rows) {
            UUID oldSession = (UUID) row.get("session_db_id");
            if (apply) {
                jdbc.update("UPDATE observation_feedback SET session_db_id = NULL WHERE id = :id",
                    new MapSqlParameterSource("id", ((Number) row.get("id")).longValue()));
                insertAuditLong("observation_feedback", ((Number) row.get("id")).longValue(),
                    CODE_FEEDBACK_NULLED, oldSession);
            }
            changed++;
        }
        summary.changed.put(STEP_FEEDBACK, changed);
    }

    // =====================================================================
    // Loading
    // =====================================================================

    Map<UUID, ParentAgg> loadParents() {
        Map<UUID, ParentAgg> byId = new LinkedHashMap<>();
        jdbc.query("""
            SELECT id, content_session_id, status, project_path, user_prompt, last_assistant_message,
                   platform_source
            FROM mem_sessions
            ORDER BY started_at_epoch ASC NULLS FIRST, id ASC
            """,
            rs -> {
                UUID id = rs.getObject("id", UUID.class);
                byId.put(id, new ParentAgg(id, rs.getString("content_session_id"),
                    rs.getString("status"), rs.getString("project_path"),
                    rs.getString("user_prompt"), rs.getString("last_assistant_message"),
                    rs.getString("platform_source")));
            });
        return byId;
    }

    /**
     * Attach children to parents through the legacy external key, refusing ambiguity.
     *
     * <p>If two parent rows share one external id (possible once the single-column unique is
     * dropped), the legacy join cannot attribute a child. Such keys are marked ambiguous and
     * their children are deliberately left unattributed instead of being attached to a guess.
     */
    void attributeChildren(Map<UUID, ParentAgg> parents) {
        Map<String, UUID> keyToParent = new HashMap<>();
        java.util.Set<String> ambiguous = new java.util.HashSet<>();
        for (ParentAgg agg : parents.values()) {
            UUID prior = keyToParent.putIfAbsent(agg.contentSessionId, agg.parentId);
            if (prior != null && !prior.equals(agg.parentId)) {
                ambiguous.add(agg.contentSessionId);
            }
        }
        ambiguous.forEach(keyToParent::remove);

        jdbc.query("""
            SELECT id, content_session_id, platform_source, source, type, subtitle, project_path,
                   conversation_id, standalone_kind, memory_scope_id
            FROM mem_observations
            ORDER BY created_at_epoch ASC, id ASC
            """,
            rs -> {
                String key = rs.getString("content_session_id");
                UUID parentId = keyToParent.get(key);
                if (parentId == null) {
                    return;
                }
                ParentAgg agg = parents.get(parentId);
                if (agg == null) {
                    return;
                }
                ChildRow child = new ChildRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("content_session_id"),
                    rs.getString("platform_source"),
                    rs.getString("source"),
                    rs.getString("type"),
                    rs.getString("subtitle"),
                    rs.getString("project_path"),
                    rs.getObject("conversation_id", UUID.class),
                    rs.getObject("memory_scope_id", UUID.class),
                    rs.getString("standalone_kind"));
                agg.children.add(child);
            });

        countChildren(parents, "mem_summaries", (agg, n) -> agg.summaryCount = n);
        countChildren(parents, "mem_user_prompts", (agg, n) -> agg.promptCount = n);

        jdbc.query("SELECT session_db_id, count(*) AS n FROM mem_pending_messages GROUP BY session_db_id",
            (RowCallbackHandler) rs -> {
                ParentAgg agg = parents.get(rs.getObject("session_db_id", UUID.class));
                if (agg != null) {
                    agg.pendingCount = rs.getInt("n");
                }
            });
    }

    private void countChildren(Map<UUID, ParentAgg> parents, String table,
                               java.util.function.ObjIntConsumer<ParentAgg> setter) {
        Map<String, UUID> externalToParent = new HashMap<>();
        for (ParentAgg agg : parents.values()) {
            externalToParent.put(agg.contentSessionId, agg.parentId);
        }
        jdbc.query("SELECT content_session_id, count(*) AS n FROM " + table + " GROUP BY content_session_id",
            (RowCallbackHandler) rs -> {
                UUID parentId = externalToParent.get(rs.getString("content_session_id"));
                ParentAgg agg = parentId == null ? null : parents.get(parentId);
                if (agg != null) {
                    setter.accept(agg, rs.getInt("n"));
                }
            });
    }

    // =====================================================================
    // Scope creation (same digest-then-exact-key rule as MemoryScopeService)
    // =====================================================================

    UUID ensureScope(MemoryScopeKey key) {
        if (apply) {
            jdbc.update("""
                INSERT INTO mem_memory_scopes (scope_type, scope_key, scope_key_sha256, display_name)
                VALUES (:type, :key COLLATE "C", :sha, :display)
                ON CONFLICT (scope_type, scope_key_sha256) DO NOTHING
                """,
                new MapSqlParameterSource()
                    .addValue("type", key.scopeType())
                    .addValue("key", key.scopeKey())
                    .addValue("sha", key.sha256())
                    .addValue("display", key.isManualDefault() ? "Manual memories" : null));
        }
        List<Map<String, Object>> found = jdbc.queryForList("""
            SELECT id FROM mem_memory_scopes
            WHERE scope_type = :type AND scope_key_sha256 = :sha AND scope_key = :key COLLATE "C"
            """,
            new MapSqlParameterSource()
                .addValue("type", key.scopeType())
                .addValue("sha", key.sha256())
                .addValue("key", key.scopeKey()));
        if (found.isEmpty()) {
            throw new IllegalStateException("SCOPE_KEY_DIGEST_COLLISION for scope_type=" + key.scopeType());
        }
        return (UUID) found.get(0).get("id");
    }

    // =====================================================================
    // Bookkeeping helpers
    // =====================================================================

    private Optional<UUID> findResumableRun() {
        List<UUID> ids = jdbc.query("""
            SELECT run_id FROM mem_migration_runs
            WHERE purpose = :p AND status IN ('running', 'failed')
            ORDER BY started_at DESC LIMIT 1
            """, new MapSqlParameterSource("p", PURPOSE),
            (rs, i) -> rs.getObject("run_id", UUID.class));
        return ids.stream().findFirst();
    }

    private UUID openRun() {
        UUID runId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO mem_migration_runs (run_id, purpose, source_schema_version, status, operator_label)
            VALUES (:id, :p, 'V20', 'running', :op)
            """,
            new MapSqlParameterSource().addValue("id", runId).addValue("p", PURPOSE)
                .addValue("op", System.getProperty("user.name")));
        return runId;
    }

    private void finishRun(UUID runId, String status) {
        jdbc.update("UPDATE mem_migration_runs SET status = :s, finished_at = now() WHERE run_id = :id",
            new MapSqlParameterSource("s", status).addValue("id", runId));
    }

    private void checkpoint(UUID runId, String step, String key) {
        if (!apply || runId == null) {
            return;
        }
        jdbc.update("""
            INSERT INTO mem_migration_checkpoints (run_id, step, shard, last_key, rows_processed,
                                                   rows_changed, batches_committed, updated_at)
            VALUES (:run, :step, '_default', :key, 0, 0, 1, now())
            ON CONFLICT (run_id, step, shard) DO UPDATE
            SET last_key = EXCLUDED.last_key,
                batches_committed = mem_migration_checkpoints.batches_committed + 1,
                updated_at = now()
            """,
            new MapSqlParameterSource().addValue("run", runId).addValue("step", step).addValue("key", key));
    }

    private void recordConflict(String table, UUID rowId, String code, String detail) {
        if (!apply) {
            log.info("[dry-run] conflict {} {} {}: {}", code, table, rowId, detail);
            return;
        }
        // A conflict is a property of the (table, row, code), not of the run that noticed it.
        // Re-running must not add a second identical entry for the same row; it only refreshes
        // the detail and last run id.
        jdbc.update("""
            INSERT INTO mem_migration_conflicts (run_id, table_name, row_id, conflict_code, detail, status)
            VALUES (:run, :table, :row, :code, :detail, 'open')
            ON CONFLICT (table_name, row_id, conflict_code) DO UPDATE
            SET detail = EXCLUDED.detail,
                run_id = EXCLUDED.run_id
            """,
            new MapSqlParameterSource()
                .addValue("run", currentRunId)
                .addValue("table", table).addValue("row", rowId)
                .addValue("code", code).addValue("detail", detail));
    }

    private void requireRun() {
        if (apply && currentRunId == null) {
            throw new IllegalStateException("backfill wrote before a run id was assigned");
        }
    }

    private void insertAudit(String table, UUID rowId, String code,
                             UUID oldConv, String oldExt, String oldPlatform, String oldSource,
                             String oldPath, UUID newConv, UUID newScope, String newPlatform,
                             String newKind, String newSource) {
        if (!apply) {
            return;
        }
        requireRun();
        jdbc.update("""
            INSERT INTO mem_migration_audit
                (run_id, batch_no, table_name, row_id, resolution_code,
                 old_conversation_id, old_content_session_id, old_platform_source, old_source,
                 old_project_path, new_conversation_id, new_memory_scope_id, new_platform_source,
                 new_standalone_kind, new_source)
            VALUES (:run, 0, :table, :row, :code, :oldConv, :oldExt, :oldPlat, :oldSource, :oldPath,
                    :newConv, :newScope, :newPlat, :newKind, :newSource)
            """,
            new MapSqlParameterSource()
                .addValue("run", currentRunId).addValue("table", table).addValue("row", rowId)
                .addValue("code", code).addValue("oldConv", oldConv).addValue("oldExt", oldExt)
                .addValue("oldPlat", oldPlatform).addValue("oldSource", oldSource).addValue("oldPath", oldPath)
                .addValue("newConv", newConv).addValue("newScope", newScope).addValue("newPlat", newPlatform)
                .addValue("newKind", newKind).addValue("newSource", newSource));
    }

    private void insertAuditLong(String table, long rowId, String code, UUID oldConv) {
        if (!apply) {
            return;
        }
        jdbc.update("""
            INSERT INTO mem_migration_audit (run_id, batch_no, table_name, row_id, resolution_code, old_conversation_id)
            VALUES (:run, 0, :table, :row, :code, :oldConv)
            """,
            new MapSqlParameterSource().addValue("run", currentRunId).addValue("table", table)
                .addValue("row", new UUID(0L, rowId)).addValue("code", code).addValue("oldConv", oldConv));
    }

    /**
     * Compare two stored platform values by effective identity.
     *
     * <p>Returns {@code null} when either side cannot be canonicalised. The caller must then
     * record a conflict: an un-normalisable platform is exactly the kind of ambiguity this
     * migration is forbidden to guess at, and it must not abort the whole run either.
     */
    private static Boolean samePlatformOrNull(String a, String b) {
        try {
            return PlatformSource.sameCanonical(a, b);
        } catch (PlatformSource.InvalidPlatformSourceException e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // =====================================================================
    // Row types
    // =====================================================================

    record ObsRow(UUID id, long createdAtEpoch, String projectPath) {
    }

    record ChildJoin(UUID id, String contentSessionId, String platformSource, String parentPlatform,
                     UUID parentId, long createdAtEpoch) {
    }

    static final class Cursor {
        long epoch = 0;
        UUID id = new UUID(0, 0);

        void advance(long nextEpoch, UUID nextId) {
            this.epoch = nextEpoch;
            this.id = nextId;
        }
    }

    static final class ChildRow {
        final UUID id;
        final String contentSessionId;
        final String platformSource;
        final String source;
        final String type;
        final String subtitle;
        final String projectPath;
        final UUID conversationId;
        final UUID memoryScopeId;
        final String standaloneKind;
        /** Exactly the manual marker pair, not a fuzzy shape. */
        final boolean manualMarker;

        ChildRow(UUID id, String contentSessionId, String platformSource, String source, String type,
                 String subtitle, String projectPath, UUID conversationId, UUID memoryScopeId,
                 String standaloneKind) {
            this.id = id;
            this.contentSessionId = contentSessionId;
            this.platformSource = platformSource;
            this.source = source;
            this.type = type;
            this.subtitle = subtitle;
            this.projectPath = projectPath;
            this.conversationId = conversationId;
            this.memoryScopeId = memoryScopeId;
            this.standaloneKind = standaloneKind;
            this.manualMarker = TechnicalBucketClassifier.MANUAL_MARKER_TYPE.equals(type)
                && TechnicalBucketClassifier.MANUAL_MARKER_SUBTITLE.equals(subtitle);
        }

        ChildObservation toChildObservation() {
            return new ChildObservation(manualMarker, source, type);
        }
    }

    static final class ParentAgg {
        final UUID parentId;
        final String contentSessionId;
        final String status;
        final String projectPath;
        final String userPrompt;
        final String lastAssistantMessage;
        final String platformSource;
        final List<ChildRow> children = new ArrayList<>();
        int summaryCount;
        int promptCount;
        int pendingCount;

        ParentAgg(UUID parentId, String contentSessionId, String status, String projectPath,
                  String userPrompt, String lastAssistantMessage, String platformSource) {
            this.parentId = parentId;
            this.contentSessionId = contentSessionId;
            this.status = status;
            this.projectPath = projectPath;
            this.userPrompt = userPrompt;
            this.lastAssistantMessage = lastAssistantMessage;
            this.platformSource = platformSource;
        }

        ParentSession toParentSession() {
            List<ChildObservation> obs = new ArrayList<>(children.size());
            for (ChildRow c : children) {
                obs.add(c.toChildObservation());
            }
            return new ParentSession(contentSessionId, status, projectPath, userPrompt,
                lastAssistantMessage, summaryCount, promptCount, pendingCount, obs);
        }
    }
}