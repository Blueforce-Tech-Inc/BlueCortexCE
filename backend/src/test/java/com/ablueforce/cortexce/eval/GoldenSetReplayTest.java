package com.ablueforce.cortexce.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * CI-safe replay: scores recorded provider answers, never calls a provider.
 *
 * <p>Fixtures are committed under {@code src/test/resources/eval/fixtures/}. When a provider could
 * not be reached (for example MiniMax returning 402 for the whole run), every entry in its fixture
 * is a failure; such a fixture is skipped here rather than turning an external balance problem into
 * a red build. The skip is explicit in the test name and in the evaluation report.</p>
 */
class GoldenSetReplayTest {

    @Test
    @DisplayName("replaying LongCat fixtures reproduces the committed metrics exactly")
    void longcatReplayReproducesMetrics() throws Exception {
        for (String name : List.of("longcat-before", "longcat-after")) {
            if (resource("/eval/fixtures/" + name + ".json") == null) {
                Assumptions.assumeTrue(false, "no recorded fixture for " + name);
            }
            replayAndCompare(name);
        }
    }

    @Test
    @DisplayName("replaying MiniMax fixtures reproduces the committed metrics exactly")
    void minimaxReplayReproducesMetrics() throws Exception {
        for (String name : List.of("minimax-before", "minimax-after")) {
            boolean present = resource("/eval/fixtures/" + name + ".json") != null;
            if (!present) {
                Assumptions.assumeTrue(false, "no recorded fixture for " + name);
            }
            if (allCallsFailed(name)) {
                Assumptions.assumeTrue(false, name + " recorded only failed calls (external balance)");
            }
            replayAndCompare(name);
        }
    }

    @Test
    @DisplayName("after fixtures never regress against the pre-change prompt")
    void afterFixturesImproveOnBefore() throws Exception {
        for (String name : List.of("longcat", "minimax")) {
            String before = "/eval/fixtures/" + name + "-before.json";
            String after = "/eval/fixtures/" + name + "-after.json";
            if (resource(before) == null || resource(after) == null) {
                continue;
            }
            if (allCallsFailed(name + "-before") || allCallsFailed(name + "-after")) {
                continue;
            }
            GoldenSetScorer.Aggregate b = score(name + "-before");
            GoldenSetScorer.Aggregate a = score(name + "-after");
            assertThat(a.formatErrors).as(name + " format errors").isZero();
            assertThat(a.skipAccuracy).as(name + " skip accuracy")
                    .isGreaterThanOrEqualTo(b.skipAccuracy);
            assertThat(a.skipFp).as(name + " must-record samples skipped")
                    .isLessThanOrEqualTo(b.skipFp);
            assertThat(a.skipFn).as(name + " low-value samples recorded")
                    .isLessThanOrEqualTo(Math.max(1, b.skipFn));
            assertThat(a.factsPrecision).as(name + " facts precision")
                    .isGreaterThanOrEqualTo(b.factsPrecision);
            assertThat(a.factsRecall).as(name + " facts recall")
                    .isGreaterThanOrEqualTo(b.factsRecall);
            assertThat(a.titleNormalizedHitRate).as(name + " normalized title hit")
                    .isGreaterThanOrEqualTo(b.titleNormalizedHitRate);
            assertThat(a.titleIdentifierHitRate).as(name + " title identifiers")
                    .isGreaterThanOrEqualTo(b.titleIdentifierHitRate);
            assertThat(a.typeAccuracy).as(name + " type accuracy")
                    .isGreaterThanOrEqualTo(b.typeAccuracy);
            assertThat(a.repeatConsistency).as(name + " repeat consistency")
                    .isGreaterThanOrEqualTo(b.repeatConsistency);
        }
    }

    private static void replayAndCompare(String name) throws Exception {
        GoldenSetScorer.Aggregate agg = score(name);
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = resource("/eval/fixtures/" + name + "-metrics.json")) {
            Assumptions.assumeTrue(in != null, "no committed metrics for " + name);
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = mapper.readValue(in, Map.class);
            assertThat(agg.total).as(name + " total").isEqualTo(intValue(expected, "total"));
            assertThat(agg.formatErrors).as(name + " format errors")
                    .isEqualTo(intValue(expected, "formatErrors"));
            assertThat(agg.skipTp).isEqualTo(intValue(expected, "skipTp"));
            assertThat(agg.skipFp).isEqualTo(intValue(expected, "skipFp"));
            assertThat(agg.skipFn).isEqualTo(intValue(expected, "skipFn"));
            assertThat(agg.skipTn).isEqualTo(intValue(expected, "skipTn"));
            assertThat(agg.skipAccuracy).as(name + " skip accuracy")
                    .isCloseTo(doubleValue(expected, "skipAccuracy"), within(1e-9));
            assertThat(agg.titleNormalizedHitRate).as(name + " title normalized")
                    .isCloseTo(doubleValue(expected, "titleNormalizedHitRate"), within(1e-9));
            assertThat(agg.titleExactHitRate).as(name + " title exact")
                    .isCloseTo(doubleValue(expected, "titleExactHitRate"), within(1e-9));
            assertThat(agg.titleActionHitRate).as(name + " title action")
                    .isCloseTo(doubleValue(expected, "titleActionHitRate"), within(1e-9));
            assertThat(agg.titleSubjectHitRate).as(name + " title subject")
                    .isCloseTo(doubleValue(expected, "titleSubjectHitRate"), within(1e-9));
            assertThat(agg.titleContractHitRate).as(name + " title contract")
                    .isCloseTo(doubleValue(expected, "titleContractHitRate"), within(1e-9));
            assertThat(agg.titleVerbContractHitRate).as(name + " title verb contract (diagnostic)")
                    .isCloseTo(doubleValue(expected, "titleVerbContractHitRate"), within(1e-9));
            assertThat(agg.factsInformativeRate).as(name + " facts informative")
                    .isCloseTo(doubleValue(expected, "factsInformativeRate"), within(1e-9));
            assertThat(agg.typeAccuracy).as(name + " type")
                    .isCloseTo(doubleValue(expected, "typeAccuracy"), within(1e-9));
            assertThat(agg.factsPrecision).as(name + " facts precision")
                    .isCloseTo(doubleValue(expected, "factsPrecision"), within(1e-9));
            assertThat(agg.factsRecall).as(name + " facts recall")
                    .isCloseTo(doubleValue(expected, "factsRecall"), within(1e-9));
            assertThat(agg.factsSupportRate).as(name + " facts support")
                    .isCloseTo(doubleValue(expected, "factsSupportRate"), within(1e-9));
            assertThat(agg.conceptsPrecision).as(name + " concepts precision")
                    .isCloseTo(doubleValue(expected, "conceptsPrecision"), within(1e-9));
            assertThat(agg.conceptsRecall).as(name + " concepts recall")
                    .isCloseTo(doubleValue(expected, "conceptsRecall"), within(1e-9));
            assertThat(agg.repeatConsistency).as(name + " repeat consistency")
                    .isCloseTo(doubleValue(expected, "repeatConsistency"), within(1e-9));
        }
    }

    private static boolean allCallsFailed(String name) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = resource("/eval/fixtures/" + name + ".json")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fixture = mapper.readValue(in, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) fixture.get("entries");
            return entries.isEmpty() || entries.stream().allMatch(entry -> entry.get("raw") == null);
        }
    }

    private static GoldenSetScorer.Aggregate score(String name) throws Exception {
        GoldenSet golden = GoldenSetEvaluator.loadGoldenSet();
        ObjectMapper mapper = new ObjectMapper();
        List<GoldenSetScorer.Outcome> outcomes = new ArrayList<>();
        try (InputStream in = resource("/eval/fixtures/" + name + ".json")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fixture = mapper.readValue(in, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) fixture.get("entries");
            for (Map<String, Object> entry : entries) {
                String id = String.valueOf(entry.get("id"));
                GoldenSet.Sample sample = GoldenSetEvaluator.find(golden, id);
                String raw = entry.get("raw") == null ? null : String.valueOf(entry.get("raw"));
                String error = entry.get("error") == null ? null : String.valueOf(entry.get("error"));
                String status = entry.get("status") == null ? null : String.valueOf(entry.get("status"));
                int runIndex = ((Number) entry.getOrDefault("runIndex", 0)).intValue();
                long elapsed = ((Number) entry.getOrDefault("elapsedMs", 0)).longValue();
                outcomes.add(GoldenSetEvaluator.toOutcome(sample, raw, error, status, elapsed,
                        runIndex, Boolean.parseBoolean(String.valueOf(fixture.getOrDefault("validate", true)))));
            }
        }
        return GoldenSetScorer.score(outcomes);
    }

    private static int intValue(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    private static double doubleValue(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }

    private static InputStream resource(String path) {
        return GoldenSetReplayTest.class.getResourceAsStream(path);
    }
}
