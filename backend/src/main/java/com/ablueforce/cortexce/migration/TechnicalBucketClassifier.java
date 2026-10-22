package com.ablueforce.cortexce.migration;

import com.ablueforce.cortexce.identity.MemoryScopeKey;

import java.util.List;
import java.util.Objects;

/**
 * Classifies a legacy "technical bucket" parent session as a compatible
 * manual / extraction / DLQ container, or records why it cannot be classified.
 *
 * <p>Frozen rules (plan §8.4 step 5 and §9.3 step 5). They are intentionally
 * <b>whole-group</b> rules: one mismatching child blocks the entire bucket, because a
 * bucket that merely *looks* technical may have been used as a real conversation.
 *
 * <p>What this class deliberately does NOT do:
 *
 * <ul>
 *   <li>It never looks at row counts, timestamps or text similarity. None of those
 *       establish lineage.</li>
 *   <li>It never inspects {@code platform_source} to decide classification. The old code
 *       could rewrite the default platform, so the platform value proves nothing.</li>
 *   <li>It never rewrites {@code source}. A {@code NULL}/blank source in a manual bucket
 *       may be <em>repaired</em> to {@code manual} (audit-backed), but a foreign non-empty
 *       source is a conflict and must stay exactly as it is.</li>
 *   <li>It never resolves {@code LEGACY_SENTINEL_IDENTITY_AMBIGUITY} on its own. The
 *       {@code manual-memories} key could genuinely have been a real session id; only an
 *       explicit, reviewed mapping can release that, never a heuristic.</li>
 * </ul>
 */
public final class TechnicalBucketClassifier {

    /** Marker type/subtitle pair written by MCP {@code save_memory}. Exact match. */
    public static final String MANUAL_MARKER_TYPE = "discovery";
    public static final String MANUAL_MARKER_SUBTITLE = "Manual memory";
    /** Compatibility wire key retained for manual rows without a project. */
    public static final String MANUAL_SENTINEL_KEY = MemoryScopeKey.LEGACY_MANUAL_PROJECT_PATH;

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_EXTRACTION = "llm_extraction";
    public static final String SOURCE_DLQ = "dlq";

    /** Literal prefixes. Never expressed as LIKE 'x_%' — {@code _} is a wildcard there. */
    public static final String EXTRACTION_TYPE_PREFIX = "extracted_";
    public static final String DLQ_TYPE_PREFIX = "dlq_";

    public static final String PARENT_STATUS_EXTRACTION = "extraction";
    public static final String PARENT_STATUS_DLQ = "dlq";
    public static final String PARENT_STATUS_COMPLETED = "completed";

    // ---- conflict codes -------------------------------------------------------
    public static final String CONFLICT_EMPTY_OBSERVATION_SET = "TECHNICAL_BUCKET_WITHOUT_OBSERVATIONS";
    public static final String CONFLICT_MIXED_CHILDREN = "TECHNICAL_BUCKET_MIXED_CHILDREN";
    public static final String CONFLICT_PARENT_STATUS_MISMATCH = "TECHNICAL_PARENT_STATUS_MISMATCH";
    public static final String CONFLICT_HAS_PROMPTS = "TECHNICAL_BUCKET_HAS_PROMPTS";
    public static final String CONFLICT_HAS_SUMMARIES = "TECHNICAL_BUCKET_HAS_SUMMARIES";
    public static final String CONFLICT_HAS_PENDING = "TECHNICAL_BUCKET_HAS_PENDING";
    public static final String CONFLICT_MANUAL_PARENT_NOT_BLANK = "MANUAL_PARENT_HAS_CONVERSATION_CONTENT";
    public static final String CONFLICT_MANUAL_PARENT_PROJECT = "MANUAL_PARENT_PROJECT_NOT_SENTINEL";
    public static final String CONFLICT_MANUAL_PARENT_STATUS = "MANUAL_PARENT_STATUS_NOT_COMPLETED";
    public static final String CONFLICT_MANUAL_FOREIGN_SOURCE = "MANUAL_MARKER_FOREIGN_SOURCE";
    public static final String CONFLICT_UNKNOWN_LEGACY_KEY = "LEGACY_TECHNICAL_KEY_UNRECOGNISED";
    public static final String CONFLICT_SENTINEL_AMBIGUITY = "LEGACY_SENTINEL_IDENTITY_AMBIGUITY";

    private TechnicalBucketClassifier() {
    }

    /** One observation row as read from the legacy table. */
    public record ChildObservation(boolean manualMarker, String source, String type) {
    }

    /** Parent session as read from the legacy table. */
    public record ParentSession(
        String contentSessionId,
        String status,
        String projectPath,
        String userPrompt,
        String lastAssistantMessage,
        int summaryCount,
        int promptCount,
        int pendingCount,
        List<ChildObservation> observations) {
    }

    /** Kind of technical bucket. */
    public enum BucketKind {
        MANUAL("manual"),
        EXTRACTION("extraction"),
        DLQ("dlq");

        private final String standaloneKind;

        BucketKind(String standaloneKind) {
            this.standaloneKind = standaloneKind;
        }

        public String standaloneKind() {
            return standaloneKind;
        }
    }

    /**
     * Result of classification. Either a proven kind plus the list of rows whose
     * {@code source} may be repaired, or a conflict code explaining the refusal.
     */
    public sealed interface Verdict permits Classified, Conflict {
        boolean isClassified();
    }

    /**
     * @param kind             proven bucket kind
     * @param repairsSourceToManual how many rows have NULL/blank source that the audit may
     *                              repair to {@code manual}
     * @param ambiguousSentinel true when the bucket is otherwise clean but its key is the
     *                          {@code manual-memories} sentinel, which cannot be proven to
     *                          have never been a real conversation (see class doc)
     */
    public record Classified(BucketKind kind, int repairsSourceToManual, boolean ambiguousSentinel)
        implements Verdict {
        @Override
        public boolean isClassified() {
            return true;
        }
    }

    /** @param code machine-readable reason; {@code detail} never contains row content */
    public record Conflict(String code, String detail) implements Verdict {
        @Override
        public boolean isClassified() {
            return false;
        }
    }

    /**
     * Classify one parent session.
     *
     * @param parent           parent row plus its children
     * @param sentinelConfirmedByReviewer true only if an out-of-band, reviewed mapping
     *                                 already discharged the sentinel-identity ambiguity
     */
    public static Verdict classify(ParentSession parent, boolean sentinelConfirmedByReviewer) {
        Objects.requireNonNull(parent, "parent");

        // 1. The manual bucket is recognised by its key AND its exact marker shape.
        if (MANUAL_SENTINEL_KEY.equals(parent.contentSessionId())) {
            return classifyManualStrict(parent, sentinelConfirmedByReviewer);
        }

        // 2. Extraction / DLQ buckets are recognised by parent status. When the status
        //    matches, the children must match too; a partial match is a conflict, never a
        //    partial conversion.
        if (PARENT_STATUS_EXTRACTION.equals(parent.status())) {
            return classifyTechnical(parent, BucketKind.EXTRACTION);
        }
        if (PARENT_STATUS_DLQ.equals(parent.status())) {
            return classifyTechnical(parent, BucketKind.DLQ);
        }

        // 3. Anything else is conversation-owned. Status 'completed'/'active' rows are
        //    NOT classified here even if they look manual: a manual marker under a key
        //    we cannot prove is the MCP bucket may be a real conversation's content.
        return new Conflict(CONFLICT_UNKNOWN_LEGACY_KEY,
            "not a recognisable technical bucket; treat as conversation-owned");
    }

    // ------------------------------------------------------------------
    // Manual
    // ------------------------------------------------------------------

    /**
     * Strict manual classification. Every condition is required; a single failure returns
     * a conflict rather than a partial match.
     */
    private static Verdict classifyManualStrict(ParentSession parent, boolean sentinelConfirmedByReviewer) {
        if (!PARENT_STATUS_COMPLETED.equals(parent.status())) {
            return new Conflict(CONFLICT_MANUAL_PARENT_STATUS,
                "manual parent status is not 'completed'");
        }
        if (!MANUAL_SENTINEL_KEY.equals(parent.projectPath())) {
            return new Conflict(CONFLICT_MANUAL_PARENT_PROJECT,
                "manual parent project_path is not the reserved sentinel");
        }
        if (!isBlank(parent.userPrompt()) || !isBlank(parent.lastAssistantMessage())) {
            return new Conflict(CONFLICT_MANUAL_PARENT_NOT_BLANK,
                "manual parent carries real conversation text");
        }
        if (parent.observations().isEmpty()) {
            return new Conflict(CONFLICT_EMPTY_OBSERVATION_SET,
                "a bucket needs at least one marked observation; an empty set proves nothing");
        }
        if (parent.summaryCount() > 0) {
            return new Conflict(CONFLICT_HAS_SUMMARIES,
                "manual bucket has summaries, which a technical bucket never produces");
        }
        if (parent.promptCount() > 0) {
            return new Conflict(CONFLICT_HAS_PROMPTS,
                "manual bucket has user prompts");
        }
        if (parent.pendingCount() > 0) {
            return new Conflict(CONFLICT_HAS_PENDING,
                "manual bucket has pending work");
        }

        int needsRepair = 0;
        for (ChildObservation child : parent.observations()) {
            if (!child.manualMarker()) {
                return new Conflict(CONFLICT_MIXED_CHILDREN,
                    "manual bucket contains an observation without the manual marker");
            }
            if (isBlank(child.source())) {
                needsRepair++;
            } else if (!SOURCE_MANUAL.equals(child.source())) {
                // Foreign non-empty source: never silently rewritten to 'manual'.
                return new Conflict(CONFLICT_MANUAL_FOREIGN_SOURCE,
                    "manual-marked observation carries a foreign non-empty source");
            }
        }

        if (!sentinelConfirmedByReviewer) {
            // Shape alone cannot prove the sentinel was never a real session id.
            return new Conflict(CONFLICT_SENTINEL_AMBIGUITY,
                "manual-memories sentinel requires an explicit reviewed mapping before classification");
        }
        return new Classified(BucketKind.MANUAL, needsRepair, true);
    }

    // ------------------------------------------------------------------
    // Extraction / DLQ
    // ------------------------------------------------------------------

    private static Verdict classifyTechnical(ParentSession parent, BucketKind kind) {
        if (parent.observations().isEmpty()) {
            return new Conflict(CONFLICT_EMPTY_OBSERVATION_SET,
                kind.standaloneKind() + " bucket has no observations");
        }
        if (parent.summaryCount() > 0) {
            return new Conflict(CONFLICT_HAS_SUMMARIES, kind.standaloneKind() + " bucket has summaries");
        }
        if (parent.promptCount() > 0) {
            return new Conflict(CONFLICT_HAS_PROMPTS, kind.standaloneKind() + " bucket has prompts");
        }
        if (parent.pendingCount() > 0) {
            return new Conflict(CONFLICT_HAS_PENDING, kind.standaloneKind() + " bucket has pending work");
        }
        String expectedSource = kind == BucketKind.EXTRACTION ? SOURCE_EXTRACTION : SOURCE_DLQ;
        String prefix = kind == BucketKind.EXTRACTION ? EXTRACTION_TYPE_PREFIX : DLQ_TYPE_PREFIX;
        for (ChildObservation child : parent.observations()) {
            if (!expectedSource.equals(child.source())) {
                return new Conflict(CONFLICT_MIXED_CHILDREN,
                    kind.standaloneKind() + " bucket has an observation with a different source");
            }
            if (child.type() == null || !child.type().startsWith(prefix)) {
                return new Conflict(CONFLICT_MIXED_CHILDREN,
                    kind.standaloneKind() + " bucket has an observation whose type lacks the literal prefix '"
                        + prefix + "'");
            }
            if (child.manualMarker()) {
                return new Conflict(CONFLICT_MIXED_CHILDREN,
                    kind.standaloneKind() + " bucket contains a manual marker observation");
            }
        }
        return new Classified(kind, 0, false);
    }

    private static boolean isBlank(String s) {
        // String.isBlank() exactly: the classifier and the preflight runner must agree.
        return s == null || s.isBlank();
    }
}