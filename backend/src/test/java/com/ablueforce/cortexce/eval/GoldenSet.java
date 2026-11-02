package com.ablueforce.cortexce.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden tool-use samples used by the manual model-quality evaluation and by the
 * offline replay test.
 * <p>
 * Kept under test resources so the offline scorer test can load the same file
 * the evaluator uses, without any network access.
 */
public class GoldenSet {

    public String version;
    public List<Sample> samples = new ArrayList<>();

    public static class Sample {
        public String id;
        public String category;
        public String toolName;
        public String cwd;
        public String occurredAt;
        public String toolInput;
        public String toolOutput;

        /** Whether this trace should produce an observation (false = should skip). */
        public boolean expectObservation;
        public String expectedType;

        /** Normalized keyword expectations for the title (case/punctuation/paraphrase tolerant). */
        public List<String> titleKeywords = new ArrayList<>();

        /** Identifiers that must appear verbatim in the title (case-sensitive). */
        public List<String> titleIdentifiers = new ArrayList<>();

        public List<String> factsKeywords = new ArrayList<>();
        public List<String> conceptsKeywords = new ArrayList<>();

        /** Re-run this sample several times to measure answer stability. */
        public boolean keySample;
        /** Informational: the trace contains more than one tool call in the same turn. */
        public boolean multiTurn;
        public String notes;
    }
}
