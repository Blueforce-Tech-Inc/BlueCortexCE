package com.ablueforce.cortexce.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden tool-use samples used by the manual model-quality evaluation.
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
        public List<String> titleKeywords = new ArrayList<>();
        public List<String> factsKeywords = new ArrayList<>();
        public List<String> conceptsKeywords = new ArrayList<>();
        public String notes;
    }
}
