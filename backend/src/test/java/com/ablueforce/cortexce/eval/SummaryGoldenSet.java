package com.ablueforce.cortexce.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Golden session-summary samples for the manual summary-quality evaluation and the offline
 * replay test. Each sample carries the observations that feed the production digest, the last
 * assistant message, and the underlying tool trace used to check that {@code request} and
 * {@code learned} are grounded in what actually happened.
 */
public class SummaryGoldenSet {

    public String version;
    public List<Sample> samples = new ArrayList<>();

    public static class Sample {
        public String id;
        public String category;
        public String lastAssistantMessage;

        /** Raw tool input/output for the whole session; the grounding evidence for learned text. */
        public String toolTrace;

        public List<Observation> observations = new ArrayList<>();

        public List<String> requestKeywords = new ArrayList<>();
        public List<String> learnedKeywords = new ArrayList<>();
        public List<String> requestIdentifiers = new ArrayList<>();
        public List<String> learnedIdentifiers = new ArrayList<>();

        public boolean keySample;
        public String notes;
    }

    public static class Observation {
        public String type;
        public String title;
        public String content;
        public List<String> facts = new ArrayList<>();
    }

    /**
     * Replicates {@code SummaryGenerationService#buildObservationDigest} so the evaluator sends
     * the production system prompt, not an approximation.
     */
    public static String digest(Sample sample) {
        StringBuilder digest = new StringBuilder();
        for (int i = 0; i < sample.observations.size(); i++) {
            Observation obs = sample.observations.get(i);
            digest.append(String.format("[%d] %s: %s", i + 1, obs.type, obs.title));
            if (obs.content != null) {
                digest.append(" - ").append(firstSentence(obs.content));
            }
            digest.append("\n");
            if (obs.content != null) {
                digest.append("  ").append(obs.content).append("\n");
            }
            if (obs.facts != null && !obs.facts.isEmpty()) {
                obs.facts.forEach(fact -> digest.append("  * ").append(fact).append("\n"));
            }
        }
        return digest.toString();
    }

    private static String firstSentence(String value) {
        int end = value.indexOf('.');
        return end < 0 ? value : value.substring(0, end + 1);
    }
}
