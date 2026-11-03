package com.ablueforce.cortexce.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Offline replay of the recorded embedding similarities. Proves the archived recall/precision
 * curve is reproducible without calling a provider, and that the threshold decision follows the
 * pre-committed rule instead of the numbers being fitted after the fact.
 */
class SearchSimilarityReplayTest {

    private static final String FIXTURE = "eval/fixtures/search-similarity-bge-m3.json";

    @Test
    @DisplayName("recorded similarities reproduce the archived curve and the chosen threshold")
    void curveReplaysExactly() throws Exception {
        SearchSimilarityEvaluator.Corpus corpus = SearchSimilarityEvaluator.loadCorpus();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> fixture;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FIXTURE)) {
            assertThat(in).as("recorded similarity fixture " + FIXTURE).isNotNull();
            fixture = mapper.readValue(in, Map.class);
        }

        Map<String, Map<String, Double>> similarities = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> stored =
                (Map<String, Map<String, Object>>) fixture.get("similarities");
        for (SearchSimilarityEvaluator.Query query : corpus.queries) {
            Map<String, Double> row = new LinkedHashMap<>();
            Map<String, Object> storedRow = stored.get(query.id);
            assertThat(storedRow).as("query " + query.id + " in the fixture").isNotNull();
            for (SearchSimilarityEvaluator.Observation observation : corpus.observations) {
                Object value = storedRow.get(observation.id);
                assertThat(value).as(query.id + " -> " + observation.id).isNotNull();
                row.put(observation.id, ((Number) value).doubleValue());
            }
            similarities.put(query.id, row);
        }

        List<SearchSimilarityEvaluator.CurvePoint> curve =
                SearchSimilarityEvaluator.curve(corpus, similarities);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recorded = (List<Map<String, Object>>) fixture.get("curve");
        assertThat(recorded).hasSize(curve.size());
        for (int i = 0; i < curve.size(); i++) {
            SearchSimilarityEvaluator.CurvePoint point = curve.get(i);
            Map<String, Object> row = recorded.get(i);
            assertThat(((Number) row.get("threshold")).doubleValue())
                    .as("threshold index " + i).isCloseTo(point.threshold, within(1e-9));
            assertThat(((Number) row.get("recall")).doubleValue())
                    .isCloseTo(point.recall, within(1e-6));
            assertThat(((Number) row.get("precision")).doubleValue())
                    .isCloseTo(point.precision, within(1e-6));
            assertThat(((Number) row.get("missedRecall")).doubleValue())
                    .isCloseTo(point.missedRecall, within(1e-6));
        }

        double chosen = ((Number) fixture.get("chosenThreshold")).doubleValue();
        assertThat(SearchSimilarityEvaluator.chooseThreshold(curve))
                .as("the archive must follow the pre-committed rule")
                .isCloseTo(chosen, within(1e-9));
        double missedAt050 = ((Number) fixture.get("missedRecallAt050")).doubleValue();
        assertThat(1.0 - SearchSimilarityEvaluator.recallAt(curve,
                SearchSimilarityEvaluator.CURRENT_FLOOR)).isCloseTo(missedAt050, within(1e-6));
    }

    @Test
    @DisplayName("recall is monotone and the corpus actually contains near-threshold pairs")
    void curveShapeAndCorpusCoverage() throws Exception {
        SearchSimilarityEvaluator.Corpus corpus = SearchSimilarityEvaluator.loadCorpus();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> fixture;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FIXTURE)) {
            assertThat(in).isNotNull();
            fixture = mapper.readValue(in, Map.class);
        }

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> stored =
                (Map<String, Map<String, Object>>) fixture.get("similarities");
        Map<String, Map<String, Double>> similarities = new LinkedHashMap<>();
        for (SearchSimilarityEvaluator.Query query : corpus.queries) {
            Map<String, Double> row = new LinkedHashMap<>();
            stored.get(query.id).forEach((id, value) -> row.put(id, ((Number) value).doubleValue()));
            similarities.put(query.id, row);
        }
        List<SearchSimilarityEvaluator.CurvePoint> curve =
                SearchSimilarityEvaluator.curve(corpus, similarities);

        for (int i = 1; i < curve.size(); i++) {
            assertThat(curve.get(i).recall)
                    .as("a stricter threshold must not raise recall")
                    .isLessThanOrEqualTo(curve.get(i - 1).recall + 1e-9);
        }

        long nearThresholdRelevant = 0;
        for (SearchSimilarityEvaluator.Query query : corpus.queries) {
            for (String id : query.relevant) {
                double similarity = similarities.get(query.id).get(id);
                if (similarity >= 0.40 && similarity <= 0.60) {
                    nearThresholdRelevant++;
                }
            }
        }
        assertThat(nearThresholdRelevant)
                .as("the corpus must exercise the 0.40-0.60 band around the 0.5 floor")
                .isGreaterThanOrEqualTo(2);
    }
}
