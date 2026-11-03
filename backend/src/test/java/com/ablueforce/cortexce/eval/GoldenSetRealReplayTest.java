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
        assertReplay(real, "minimax-real-r12-candidates");
    }

    @Test
    @DisplayName("replaying the LongCat real-history fixture reproduces its committed metrics")
    void longcatRealReplayReproducesMetrics() throws Exception {
        GoldenSet real = GoldenSetEvaluator.loadGoldenSet("real");
        assertReplay(real, "longcat-real-r12-candidates");
    }

    @Test
    @DisplayName("replaying the round-13 MiniMax fixture reproduces its committed metrics")
    void minimaxR13ReplayReproducesMetrics() throws Exception {
        GoldenSet real = GoldenSetEvaluator.loadGoldenSet("real");
        assertReplay(real, "minimax-real-r13-final");
    }

    @Test
    @DisplayName("replaying the round-13 LongCat fixture reproduces its committed metrics")
    void longcatR13ReplayReproducesMetrics() throws Exception {
        GoldenSet real = GoldenSetEvaluator.loadGoldenSet("real");
        assertReplay(real, "longcat-real-r13-final");
    }

    /**
     * Round 12 gate. The identifier and subject floors are the 90% target itself, not a
     * measurement to be lowered later: a gate may only rise. If a fixture misses it the build
     * stays red until the extraction improves, and a permanently unreachable floor has to be
     * argued in the plan document before anyone touches this constant.
     */
    @Test
    @DisplayName("the real-history fixtures clear the 90% identifier and subject floors")
    void realFixturesClearRatchetedFloors() throws Exception {
        GoldenSet real = GoldenSetEvaluator.loadGoldenSet("real");
        assertFloors(real, "minimax-real-r13-final");
        assertFloors(real, "longcat-real-r13-final");
    }

    private static void assertFloors(GoldenSet real, String name) throws Exception {
        GoldenSetScorer.Aggregate agg = score(real, name);
        Map<String, Double> gates = evalGates();
        assertThat(agg.titleActionHitRate).as(name + " action class")
                .isGreaterThanOrEqualTo(gates.get("action"));
        assertThat(agg.typeAccuracyAuditable).as(name + " auditable type")
                .isGreaterThanOrEqualTo(gates.get("typeAuditable"));
        assertThat(agg.titleIdentifierHitRate).as(name + " identifier")
                .isGreaterThanOrEqualTo(gates.get("identifier"));
        assertThat(agg.titleTraceSubjectHitRate).as(name + " trace-visible subject")
                .isGreaterThanOrEqualTo(gates.get("traceSubject"));
        Map<String, Object> metrics = metrics(name);
        int calls = ((Number) metrics.get("calls")).intValue();
        int failures = ((Number) metrics.get("callFailures")).intValue();
        int allowed = GoldenSetScorer.allowedCallFailures(calls, gates.get("callSuccessRate"));
        assertThat(failures).as(name + " provider call failures (allowance " + allowed + "/"
                        + calls + " calls)")
                .isLessThanOrEqualTo(allowed);
    }

    private static Map<String, Object> metrics(String name) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = resource("/eval/fixtures/" + name + "-metrics.json")) {
            assertThat(in).as("committed metrics for " + name).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = mapper.readValue(in, Map.class);
            return metrics;
        }
    }

    /**
     * Gate floors live in {@code scripts/eval-gates.json}, and the committed high-water mark
     * lives beside it. A floor may only rise: this test fails when the current file drops below
     * the recorded floor, so a low measurement can never be turned into a green build by editing
     * the gate. Lowering would require editing the floor file too, which is visible in review.
     */
    @Test
    @DisplayName("the evaluation gate floors never move down")
    void gateFloorsAreMonotonic() throws Exception {
        Map<String, Double> current = evalGates();
        Map<String, Double> floor = evalGates("eval-gates-floor.json");
        for (String key : current.keySet()) {
            assertThat(current.get(key)).as("gate " + key).isGreaterThanOrEqualTo(floor.get(key));
        }
    }

    private static Map<String, Double> evalGates() throws Exception {
        return evalGates("eval-gates.json");
    }

    private static Map<String, Double> evalGates(String file) throws Exception {
        java.nio.file.Path path = null;
        for (String candidate : new String[]{"scripts/" + file, "../scripts/" + file}) {
            java.nio.file.Path candidatePath = java.nio.file.Paths.get(candidate);
            if (java.nio.file.Files.exists(candidatePath)) {
                path = candidatePath;
                break;
            }
        }
        assertThat(path).as("gate file " + file).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = new ObjectMapper().readValue(
                java.nio.file.Files.readString(path), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> real = (Map<String, Object>) parsed.get("real");
        Map<String, Double> gates = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : real.entrySet()) {
            gates.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
        }
        return gates;
    }

    private static void assertReplay(GoldenSet real, String name) throws Exception {
        GoldenSetScorer.Aggregate agg = score(real, name);

        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = resource("/eval/fixtures/" + name + "-metrics.json")) {
            assertThat(in).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = mapper.readValue(in, Map.class);
            assertThat(agg.total).isEqualTo(intValue(expected, "total"));
            assertThat(agg.formatErrors).isEqualTo(intValue(expected, "formatErrors"));
            assertThat(agg.titleIdentifierHitRate)
                    .isCloseTo(doubleValue(expected, "titleIdentifierHitRate"), within(1e-9));
            assertThat(agg.titleActionHitRate)
                    .isCloseTo(doubleValue(expected, "titleActionHitRate"), within(1e-9));
            assertThat(agg.titleTraceSubjectHitRate)
                    .isCloseTo(doubleValue(expected, "titleTraceSubjectHitRate"), within(1e-9));
            assertThat(agg.typeAccuracy)
                    .isCloseTo(doubleValue(expected, "typeAccuracy"), within(1e-9));
            assertThat(agg.typeAccuracyAuditable)
                    .isCloseTo(doubleValue(expected, "typeAccuracyAuditable"), within(1e-9));
            assertThat(agg.typeUnverifiable)
                    .isEqualTo(intValue(expected, "typeUnverifiable"));
            assertThat(agg.callFailures).isEqualTo(intValue(expected, "callFailures"));
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
