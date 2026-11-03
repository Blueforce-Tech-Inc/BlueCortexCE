package com.ablueforce.cortexce.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * CI-safe replay of the real-history fixtures. These are scored against the generated
 * set, never mixed with the hand-written numbers, and never call a provider.
 */
class GoldenSetRealReplayTest {

    @Test
    @DisplayName("replaying the MiniMax real-history fixture reproduces its committed metrics")
    void minimaxRealReplayReproducesMetrics() throws Exception {
        GoldenSet real = GoldenSetEvaluator.loadGoldenSet("real");
        GoldenSetScorer.Aggregate agg = score(real, "minimax-real-v9");

        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = resource("/eval/fixtures/minimax-real-v9-metrics.json")) {
            assertThat(in).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = mapper.readValue(in, Map.class);
            assertThat(agg.total).isEqualTo(intValue(expected, "total"));
            assertThat(agg.formatErrors).isEqualTo(intValue(expected, "formatErrors"));
            assertThat(agg.titleIdentifierHitRate)
                    .isCloseTo(doubleValue(expected, "titleIdentifierHitRate"), within(1e-9));
            assertThat(agg.titleTraceSubjectHitRate)
                    .isCloseTo(doubleValue(expected, "titleTraceSubjectHitRate"), within(1e-9));
            assertThat(agg.titleActionHitRate)
                    .isCloseTo(doubleValue(expected, "titleActionHitRate"), within(1e-9));
            assertThat(agg.typeAccuracy)
                    .isCloseTo(doubleValue(expected, "typeAccuracy"), within(1e-9));
            assertThat(agg.p95Tokens).isEqualTo(((Number) expected.get("p95Tokens")).longValue());
        }
    }

    private static GoldenSetScorer.Aggregate score(GoldenSet real, String name) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<GoldenSetScorer.Outcome> outcomes = new ArrayList<>();
        try (InputStream in = resource("/eval/fixtures/" + name + ".json")) {
            assertThat(in).as("committed fixture for " + name).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> fixture = mapper.readValue(in, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) fixture.get("entries");
            for (Map<String, Object> entry : entries) {
                GoldenSet.Sample sample = find(real, String.valueOf(entry.get("id")));
                String raw = entry.get("raw") == null ? null : String.valueOf(entry.get("raw"));
                String error = entry.get("error") == null ? null : String.valueOf(entry.get("error"));
                String status = entry.get("status") == null ? null : String.valueOf(entry.get("status"));
                long elapsed = ((Number) entry.getOrDefault("elapsedMs", 0)).longValue();
                int runIndex = ((Number) entry.getOrDefault("runIndex", 0)).intValue();
                int promptTokens = ((Number) entry.getOrDefault("promptTokens", 0)).intValue();
                int completionTokens = ((Number) entry.getOrDefault("completionTokens", 0)).intValue();
                outcomes.add(GoldenSetEvaluator.toOutcome(sample, raw, error, status, elapsed,
                        runIndex, true, promptTokens, completionTokens));
            }
        }
        return GoldenSetScorer.score(outcomes);
    }

    private static GoldenSet.Sample find(GoldenSet set, String id) {
        return set.samples.stream().filter(sample -> sample.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no real sample " + id));
    }

    private static int intValue(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    private static double doubleValue(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }

    private static InputStream resource(String path) {
        return GoldenSetRealReplayTest.class.getResourceAsStream(path);
    }
}
