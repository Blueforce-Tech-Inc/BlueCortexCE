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
 * <p>Cost is linear in attempts: both latency and token spend multiply, which is why the
 * number of attempts is a configuration value and defaults to one (off).</p>
 */
final class ExtractionConsistency {

    private ExtractionConsistency() {
    }

    record Candidate(boolean skipped, boolean parsed, String type, String title) {
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

    /** @return the index of the winning candidate, or -1 when nothing was usable. */
    static int select(List<Candidate> candidates) {
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
