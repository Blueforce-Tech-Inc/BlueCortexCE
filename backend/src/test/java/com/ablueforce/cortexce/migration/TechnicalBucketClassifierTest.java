package com.ablueforce.cortexce.migration;

import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.BucketKind;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.ChildObservation;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.Classified;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.Conflict;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.ParentSession;
import com.ablueforce.cortexce.migration.TechnicalBucketClassifier.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classifier decides whether a legacy row group may stop pretending to be a real
 * conversation. Every rejection case here is a "fail closed" guarantee from the plan:
 * shape alone is never enough, and a single mismatching child blocks the whole bucket.
 */
class TechnicalBucketClassifierTest {

    private static final String MANUAL_KEY = TechnicalBucketClassifier.MANUAL_SENTINEL_KEY;

    private static ChildObservation manual(String source) {
        return new ChildObservation(true, source, "discovery");
    }

    private static ChildObservation extraction() {
        return new ChildObservation(false, "llm_extraction", "extracted_user_preference");
    }

    private static ChildObservation dlq() {
        return new ChildObservation(false, "dlq", "dlq_user_preference");
    }

    private static ParentSession manualParent(List<ChildObservation> obs) {
        return new ParentSession(MANUAL_KEY, "completed", MANUAL_KEY, "", null, 0, 0, 0, obs);
    }

    private static ParentSession extractionParent(List<ChildObservation> obs) {
        return new ParentSession("ext:aaaa:user-a", "extraction", "/p", null, null, 0, 0, 0, obs);
    }

    private static ParentSession dlqParent(List<ChildObservation> obs) {
        return new ParentSession("dlq:extraction", "dlq", "/p", null, null, 0, 0, 0, obs);
    }

    @Nested
    @DisplayName("manual bucket")
    class Manual {

        @Test
        void cleanBucketIsClassifiedOnlyWithReviewedSentinelMapping() {
            ParentSession p = manualParent(List.of(manual(null), manual("manual")));
            Verdict free = TechnicalBucketClassifier.classify(p, false);
            Conflict c = assertInstanceOf(Conflict.class, free);
            assertEquals(TechnicalBucketClassifier.CONFLICT_SENTINEL_AMBIGUITY, c.code(),
                "shape alone must not release the sentinel ambiguity");

            Verdict released = TechnicalBucketClassifier.classify(p, true);
            Classified k = assertInstanceOf(Classified.class, released);
            assertEquals(BucketKind.MANUAL, k.kind());
            assertEquals(1, k.repairsSourceToManual(), "only the NULL-source row may be repaired");
        }

        @Test
        void blankSourceCountsAsRepairNotConflict() {
            ParentSession p = manualParent(List.of(manual(""), manual("   "), manual("　")));
            Classified k = assertInstanceOf(Classified.class,
                TechnicalBucketClassifier.classify(p, true));
            assertEquals(3, k.repairsSourceToManual());
        }

        @Test
        @DisplayName("a foreign non-empty source is a conflict, never rewritten")
        void foreignSourceIsConflict() {
            ParentSession p = manualParent(List.of(manual(null), manual("llm_extraction")));
            Conflict c = assertInstanceOf(Conflict.class,
                TechnicalBucketClassifier.classify(p, true));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MANUAL_FOREIGN_SOURCE, c.code());
        }

        @Test
        void mixedMarkerBlocksWholeBucket() {
            ParentSession p = manualParent(List.of(
                manual(null),
                new ChildObservation(false, "tool_result", "bugfix")));
            Conflict c = assertInstanceOf(Conflict.class,
                TechnicalBucketClassifier.classify(p, true));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MIXED_CHILDREN, c.code());
        }

        @Test
        void emptyObservationSetProvesNothing() {
            Conflict c = assertInstanceOf(Conflict.class,
                TechnicalBucketClassifier.classify(manualParent(List.of()), true));
            assertEquals(TechnicalBucketClassifier.CONFLICT_EMPTY_OBSERVATION_SET, c.code());
        }

        @Test
        void nonBlankParentConversationTextBlocksClassification() {
            ParentSession withPrompt = new ParentSession(
                MANUAL_KEY, "completed", MANUAL_KEY, "a real prompt", null, 0, 0, 0, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MANUAL_PARENT_NOT_BLANK,
                ((Conflict) TechnicalBucketClassifier.classify(withPrompt, true)).code());

            ParentSession withAnswer = new ParentSession(
                MANUAL_KEY, "completed", MANUAL_KEY, "", "assistant said something", 0, 0, 0, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MANUAL_PARENT_NOT_BLANK,
                ((Conflict) TechnicalBucketClassifier.classify(withAnswer, true)).code());
        }

    @Test
    @DisplayName("blank follows String.isBlank(): Unicode spaces count, a no-break space does not")
    void unicodeWhitespaceFollowsStringIsBlank() {
        // U+3000 ideographic space and U+2003 em space are Character.isWhitespace.
        ParentSession blank = new ParentSession(
            MANUAL_KEY, "completed", MANUAL_KEY, "\u3000\u2003", "\u2003", 0, 0, 0, List.of(manual(null)));
        assertTrue(TechnicalBucketClassifier.classify(blank, true) instanceof Classified,
            "Unicode-whitespace-only conversation text is blank");

        // U+00A0 is NOT Character.isWhitespace, so String.isBlank() is false. Treating it
        // as empty would let a bucket with real (if odd) content be reclassified.
        ParentSession nbsp = new ParentSession(
            MANUAL_KEY, "completed", MANUAL_KEY, "\u00A0", "", 0, 0, 0, List.of(manual(null)));
        assertEquals(TechnicalBucketClassifier.CONFLICT_MANUAL_PARENT_NOT_BLANK,
            ((Conflict) TechnicalBucketClassifier.classify(nbsp, true)).code());
    }

        @Test
        void relatedChildRowsBlockClassification() {
            ParentSession withSummary = new ParentSession(
                MANUAL_KEY, "completed", MANUAL_KEY, "", "", 1, 0, 0, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_HAS_SUMMARIES,
                ((Conflict) TechnicalBucketClassifier.classify(withSummary, true)).code());

            ParentSession withPrompt = new ParentSession(
                MANUAL_KEY, "completed", MANUAL_KEY, "", "", 0, 1, 0, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_HAS_PROMPTS,
                ((Conflict) TechnicalBucketClassifier.classify(withPrompt, true)).code());

            ParentSession withPending = new ParentSession(
                MANUAL_KEY, "completed", MANUAL_KEY, "", "", 0, 0, 1, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_HAS_PENDING,
                ((Conflict) TechnicalBucketClassifier.classify(withPending, true)).code());
        }

        @Test
        void parentShapeMustMatch() {
            ParentSession active = new ParentSession(
                MANUAL_KEY, "active", MANUAL_KEY, "", "", 0, 0, 0, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MANUAL_PARENT_STATUS,
                ((Conflict) TechnicalBucketClassifier.classify(active, true)).code());

            ParentSession wrongPath = new ParentSession(
                MANUAL_KEY, "completed", "/some/project", "", "", 0, 0, 0, List.of(manual(null)));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MANUAL_PARENT_PROJECT,
                ((Conflict) TechnicalBucketClassifier.classify(wrongPath, true)).code());
        }
    }

    @Nested
    @DisplayName("extraction bucket")
    class Extraction {

        @Test
        void cleanBucketIsClassifiedWithoutAnyReviewerRelease() {
            Classified k = assertInstanceOf(Classified.class,
                TechnicalBucketClassifier.classify(
                    extractionParent(List.of(extraction(), extraction())), false));
            assertEquals(BucketKind.EXTRACTION, k.kind());
            assertEquals(0, k.repairsSourceToManual());
        }

        @Test
        void emptyBucketIsAConflict() {
            assertEquals(TechnicalBucketClassifier.CONFLICT_EMPTY_OBSERVATION_SET,
                ((Conflict) TechnicalBucketClassifier.classify(extractionParent(List.of()), false)).code());
        }

        @Test
        void mixedChildBlocksWholeBucket() {
            ParentSession p = extractionParent(List.of(
                extraction(),
                new ChildObservation(false, "tool_result", "bugfix")));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MIXED_CHILDREN,
                ((Conflict) TechnicalBucketClassifier.classify(p, false)).code());
        }

        @Test
        @DisplayName("the type prefix is literal: 'extracted' without '_' does not match")
        void typePrefixIsLiteral() {
            ParentSession p = extractionParent(List.of(
                new ChildObservation(false, "llm_extraction", "extractedX")));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MIXED_CHILDREN,
                ((Conflict) TechnicalBucketClassifier.classify(p, false)).code());
            // and a value that a LIKE 'extracted_%' pattern would wrongly match
            ParentSession likeTrap = extractionParent(List.of(
                new ChildObservation(false, "llm_extraction", "extracted_ok")));
            assertTrue(TechnicalBucketClassifier.classify(likeTrap, false) instanceof Classified);
        }

        @Test
        void aManualMarkerInsideExtractionBucketIsMixed() {
            ParentSession p = extractionParent(List.of(extraction(), manual("manual")));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MIXED_CHILDREN,
                ((Conflict) TechnicalBucketClassifier.classify(p, false)).code());
        }
    }

    @Nested
    @DisplayName("DLQ bucket")
    class Dlq {

        @Test
        void cleanBucketIsClassified() {
            Classified k = assertInstanceOf(Classified.class,
                TechnicalBucketClassifier.classify(dlqParent(List.of(dlq())), false));
            assertEquals(BucketKind.DLQ, k.kind());
        }

        @Test
        void strayPromptBlocksClassification() {
            ParentSession p = new ParentSession(
                "dlq:with-prompt", "dlq", "/p", null, null, 0, 1, 0, List.of(dlq()));
            assertEquals(TechnicalBucketClassifier.CONFLICT_HAS_PROMPTS,
                ((Conflict) TechnicalBucketClassifier.classify(p, false)).code());
        }

        @Test
        void wrongSourceBlocksClassification() {
            ParentSession p = dlqParent(List.of(new ChildObservation(false, "llm_extraction", "dlq_x")));
            assertEquals(TechnicalBucketClassifier.CONFLICT_MIXED_CHILDREN,
                ((Conflict) TechnicalBucketClassifier.classify(p, false)).code());
        }
    }

    @Nested
    @DisplayName("non-technical rows stay conversations")
    class Conversations {

        @Test
        void ordinaryCompletedSessionIsNotATechnicalBucket() {
            ParentSession p = new ParentSession("conv-ordinary", "completed", "/work/alpha",
                "fix login", "done", 2, 1, 3, List.of(new ChildObservation(false, "tool_result", "bugfix")));
            Conflict c = assertInstanceOf(Conflict.class, TechnicalBucketClassifier.classify(p, true));
            assertEquals(TechnicalBucketClassifier.CONFLICT_UNKNOWN_LEGACY_KEY, c.code());
            assertFalse(c.isClassified());
        }

        @Test
        @DisplayName("manual markers under a non-sentinel key are not auto-classified")
        void manualMarkersUnderRealKeyStayConversations() {
            // e.g. a real session whose observation happened to use the manual type/subtitle
            ParentSession p = new ParentSession("conv-real", "completed", "/work/alpha",
                "prompt", "answer", 0, 0, 0, List.of(manual("manual")));
            Conflict c = assertInstanceOf(Conflict.class, TechnicalBucketClassifier.classify(p, true));
            assertEquals(TechnicalBucketClassifier.CONFLICT_UNKNOWN_LEGACY_KEY, c.code());
        }

        @Test
        void platformValueIsNeverConsulted() {
            // No ParentSession field carries platform at all, by construction: the old code
            // could rewrite the default platform, so it proves nothing about classification.
            ParentSession p = manualParent(List.of(manual(null)));
            assertFalse(java.util.Arrays.stream(ParentSession.class.getRecordComponents())
                .anyMatch(rc -> rc.getName().toLowerCase().contains("platform")));
        }
    }
}