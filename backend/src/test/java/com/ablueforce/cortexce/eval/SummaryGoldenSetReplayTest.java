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
 * CI-safe replay of the session-summary fixtures: scores recorded provider answers,
 * never calls a provider.
 *
 * <p>Each fixture reproduces its committed metrics exactly, so a change to the summary
 * prompt, the parser, the trace-support rule or the scorer cannot silently move the
 * numbers that were used to fix the A7–A10 thresholds.</p>
 */
class SummaryGoldenSetReplayTest {

    @Test
    @DisplayName("replaying LongCat summary fixtures reproduces the committed baseline")
    void longcatReplayReproducesBaseline() throws Exception {
        replayAndCompare("summary-longcat-baseline");
    }

    @Test
    @DisplayName("replaying MiniMax summary fixtures reproduces the committed baseline")
    void minimaxReplayReproducesBaseline() throws Exception {
        replayAndCompare("summary-minimax-baseline");
    }

    @Test
    @DisplayName("both committed baselines clear the A7–A10 floors")
    void baselinesClearTheGates() throws Exception {
        SummaryGoldenSetScorer.Aggregate longcat = score("summary-longcat-baseline");
        SummaryGoldenSetScorer.Aggregate minimax = score("summary-minimax-baseline");

        assertThat(longcat.requestCoverage)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.REQUEST_COVERAGE_FLOOR);
        assertThat(longcat.requestSupportRate)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.REQUEST_SUPPORT_FLOOR);
        assertThat(longcat.learnedRecall)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.LEARNED_RECALL_FLOOR);
        assertThat(longcat.learnedSupportRate)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.LEARNED_SUPPORT_FLOOR);

        assertThat(minimax.requestCoverage)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.REQUEST_COVERAGE_FLOOR);
        assertThat(minimax.requestSupportRate)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.REQUEST_SUPPORT_FLOOR);
        assertThat(minimax.learnedRecall)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.LEARNED_RECALL_FLOOR);
        assertThat(minimax.learnedSupportRate)
            .isGreaterThanOrEqualTo(SummaryGoldenSetEvaluator.LEARNED_SUPPORT_FLOOR);
    }

    private static void replayAndCompare(String name) throws Exception {
        SummaryGoldenSetScorer.Aggregate agg = score(name);
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = resource("/eval/fixtures/" + name + "-metrics.json")) {
            assertThat(in).as("committed metrics for " + name).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = mapper.readValue(in, Map.class);
            assertThat(agg.total).as(name + " total").isEqualTo(intValue(expected, "total"));
            assertThat(agg.callFailures).as(name + " call failures")
                .isEqualTo(intValue(expected, "callFailures"));
            assertThat(agg.formatErrors).as(name + " format errors")
                .isEqualTo(intValue(expected, "formatErrors"));
            assertThat(agg.requestCoverage).isCloseTo(
                doubleValue(expected, "requestCoverage"), within(1e-9));
            assertThat(agg.requestIdentifierHitRate).isCloseTo(
                doubleValue(expected, "requestIdentifierHitRate"), within(1e-9));
            assertThat(agg.requestSupportRate).isCloseTo(
                doubleValue(expected, "requestSupportRate"), within(1e-9));
            assertThat(agg.learnedRecall).isCloseTo(
                doubleValue(expected, "learnedRecall"), within(1e-9));
            assertThat(agg.learnedIdentifierHitRate).isCloseTo(
                doubleValue(expected, "learnedIdentifierHitRate"), within(1e-9));
            assertThat(agg.learnedSupportRate).isCloseTo(
                doubleValue(expected, "learnedSupportRate"), within(1e-9));
            assertThat(agg.p50Ms).isEqualTo(((Number) expected.get("p50Ms")).longValue());
            assertThat(agg.p95Ms).isEqualTo(((Number) expected.get("p95Ms")).longValue());
            assertThat(agg.maxMs).isEqualTo(((Number) expected.get("maxMs")).longValue());
        }
    }

    private static SummaryGoldenSetScorer.Aggregate score(String name) throws Exception {
        SummaryGoldenSet golden = SummaryGoldenSetEvaluator.loadGoldenSet();
        ObjectMapper mapper = new ObjectMapper();
        List<SummaryGoldenSetScorer.Outcome> outcomes = new ArrayList<>();
        try (InputStream in = resource("/eval/fixtures/" + name + ".json")) {
            assertThat(in).as("committed fixture for " + name).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> fixture = mapper.readValue(in, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) fixture.get("entries");
            for (Map<String, Object> entry : entries) {
                String id = String.valueOf(entry.get("id"));
                SummaryGoldenSet.Sample sample = SummaryGoldenSetEvaluator.find(golden, id);
                String raw = entry.get("raw") == null ? null : String.valueOf(entry.get("raw"));
                String error = entry.get("error") == null ? null : String.valueOf(entry.get("error"));
                String status = entry.get("status") == null ? null : String.valueOf(entry.get("status"));
                long elapsed = ((Number) entry.getOrDefault("elapsedMs", 0)).longValue();
                int runIndex = ((Number) entry.getOrDefault("runIndex", 0)).intValue();
                outcomes.add(SummaryGoldenSetEvaluator.toOutcome(
                    sample, raw, error, status, elapsed, runIndex));
            }
        }
        return SummaryGoldenSetScorer.score(outcomes);
    }

    private static int intValue(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }

    private static double doubleValue(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).doubleValue();
    }

    private static InputStream resource(String path) {
        return SummaryGoldenSetReplayTest.class.getResourceAsStream(path);
    }
}
