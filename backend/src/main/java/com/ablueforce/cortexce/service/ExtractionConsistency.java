package com.ablueforce.cortexce.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Majority vote over repeated extraction attempts of the same tool trace.
 *
 * <p>Temperature 0 does not make providers deterministic. When the caller enables more than
 * one attempt, the winning answer is the most frequent {@code skip}/{@code unparsed}/
 * {@code type + leading title verb} signature; ties keep the earliest answer so the result
 * is deterministic given the same responses. Unusable attempts never vote.</p>
 *
 * <p>Cost is linear in attempts: token spend multiplies. The attempts run concurrently in
 * {@code AgentService}, so wall-clock latency stays close to a single call. Because the
 * token floor only allows 1.5x, a repeat is triggered only for a low-confidence first
 * answer (see {@link #needsSecondOpinion(Candidate)}); a well-formed answer is accepted
 * as-is and the configured attempt count is an upper bound, not a fixed cost.</p>
 */
public final class ExtractionConsistency {

    private ExtractionConsistency() {
    }

    public record Candidate(boolean skipped, boolean parsed, String type, String title) {
        boolean usable() {
            return skipped || parsed;
        }

        String signature() {
            if (skipped) {
                return "skip";
            }
            if (!parsed) {
                return "unparsed";
            }
            return (type == null ? "" : type) + "|" + titleLead(title);
        }

        private static String titleLead(String title) {
            if (title == null || title.isBlank()) {
                return "";
            }
            String trimmed = title.trim();
            int end = 0;
            while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
                end++;
            }
            return trimmed.substring(0, end).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * A first answer needs a second opinion only when it is malformed: the provider call
     * failed, the XML did not parse, or the parsed answer carries no type or title. A skip
     * is a valid answer and never repeats. Well-formed answers are not re-sampled because
     * three calls per trace exceed the token floor, not because their label is trusted.
     */
    public static boolean needsSecondOpinion(Candidate candidate) {
        if (candidate == null) {
            return true;
        }
        if (candidate.skipped()) {
            return false;
        }
        if (!candidate.parsed()) {
            return true;
        }
        return candidate.type() == null || candidate.type().isBlank()
                || candidate.title() == null || candidate.title().isBlank();
    }

    /** @return the index of the winning candidate, or -1 when nothing was usable. */
    public static int select(List<Candidate> candidates) {
        List<Candidate> usable = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).usable()) {
                usable.add(candidates.get(i));
                indexes.add(i);
            }
        }
        if (usable.isEmpty()) {
            return -1;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> firstIndex = new LinkedHashMap<>();
        for (int i = 0; i < usable.size(); i++) {
            String signature = usable.get(i).signature();
            counts.merge(signature, 1, Integer::sum);
            firstIndex.putIfAbsent(signature, indexes.get(i));
        }
        String winner = null;
        int best = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                winner = entry.getKey();
            }
        }
        return firstIndex.get(winner);
    }
}
