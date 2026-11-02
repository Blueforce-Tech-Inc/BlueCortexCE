package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.util.XmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline coverage for the golden-set scoring rules and sample set shape.
 * <p>
 * These tests never call a provider: they feed synthetic outcomes into the
 * scorer, so CI stays offline while the manual evaluator uses the same code.
 */
class GoldenSetScorerTest {

    @Test
    @DisplayName("a perfect observation scores full contract, title, type and fields")
    void perfectObservationScoresFullMarks() {
        GoldenSet.Sample sample = sample(true, "bugfix",
                List.of("NullPointer", "PaymentService"), List.of("orderId", "PaymentNotFoundException"),
                List.of("problem-solution", "pattern"));
        GoldenSetScorer.Outcome outcome = new GoldenSetScorer.Outcome(sample, "<observation/>",
                false, observation("bugfix", "Fixed NullPointer in PaymentService",
                        List.of("orderId is null", "PaymentNotFoundException returned"),
                        List.of("problem-solution", "pattern")), false, null, 100);

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(outcome));

        assertEquals(1.0, agg.contractPassRate);
        assertEquals(1.0, agg.skipAccuracy);
        assertEquals(1.0, agg.titleFullHitRate);
        assertEquals(1.0, agg.typeAccuracy);
        assertEquals(1.0, agg.factsRecall);
        assertEquals(1.0, agg.factsPrecision);
        assertEquals(1.0, agg.conceptsRecall);
        assertEquals(0, agg.formatErrors);
        assertTrue(agg.problems.isEmpty());
    }

    @Test
    @DisplayName("a plain-text answer counts as a format error, not a pass")
    void plainTextCountsAsFormatError() {
        GoldenSet.Sample sample = sample(true, "refactor",
                List.of("InvoiceService"), List.of("TaxCalculator"), List.of("what-changed"));
        GoldenSetScorer.Outcome outcome = new GoldenSetScorer.Outcome(sample,
                "I refactored InvoiceService into three classes.", false, null, false,
                "DataValidationException: No <observation> block found in XML", 100);

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(outcome));

        assertEquals(0.0, agg.contractPassRate);
        assertEquals(1, agg.formatErrors);
        assertEquals(0.0, agg.titleFullHitRate);
        assertEquals(1, agg.problems.size());
    }

    @Test
    @DisplayName("an expected observation that the model skips is a skip false positive")
    void expectedObservationButSkippedIsFalsePositive() {
        GoldenSet.Sample sample = sample(true, "bugfix",
                List.of("cache"), List.of("computeIfAbsent"), List.of("problem-solution"));
        GoldenSetScorer.Outcome outcome = new GoldenSetScorer.Outcome(sample,
                "<skip>routine change</skip>", true, null, false, null, 100);

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(outcome));

        assertEquals(0.0, agg.contractPassRate);
        assertEquals(1, agg.skipFp);
        assertEquals(0, agg.skipTn);
        assertEquals(0.0, agg.skipAccuracy);
        assertTrue(agg.problems.get(0).contains("expected an observation"));
    }

    @Test
    @DisplayName("an expected skip that produces an observation is a skip false negative")
    void expectedSkipButProducedIsFalseNegative() {
        GoldenSet.Sample sample = sample(false, null, List.of(), List.of(), List.of());
        GoldenSetScorer.Outcome outcome = new GoldenSetScorer.Outcome(sample,
                "<observation>...</observation>", false,
                observation("discovery", "Read Button.tsx", List.of(), List.of()), false, null, 100);

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(outcome));

        assertEquals(0.0, agg.contractPassRate);
        assertEquals(0, agg.skipTp);
        assertEquals(1, agg.skipFn);
        assertTrue(agg.problems.get(0).contains("expected <skip>"));
    }

    @Test
    @DisplayName("a correct skip passes the contract")
    void expectedSkipAndSkippedPasses() {
        GoldenSet.Sample sample = sample(false, null, List.of(), List.of(), List.of());
        GoldenSetScorer.Outcome outcome = new GoldenSetScorer.Outcome(sample,
                "<skip>simple listing</skip>", true, null, false, null, 100);

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(outcome));

        assertEquals(1.0, agg.contractPassRate);
        assertEquals(1, agg.skipTp);
        assertEquals(1.0, agg.skipAccuracy);
        assertTrue(agg.problems.isEmpty());
    }

    @Test
    @DisplayName("the golden set is 10-15 samples and covers every required category")
    void goldenSetCoversRequiredCategories() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GoldenSet golden;
        try (InputStream in = GoldenSetScorerTest.class.getResourceAsStream("/eval/golden-tool-use.json")) {
            assertNotNull(in, "golden-tool-use.json must be on the test classpath");
            golden = mapper.readValue(in, GoldenSet.class);
        }

        assertTrue(golden.samples.size() >= 10 && golden.samples.size() <= 15,
                "expected 10-15 samples, got " + golden.samples.size());

        Set<String> categories = golden.samples.stream()
                .map(s -> s.category).collect(Collectors.toSet());
        Set<String> required = Set.of("bug-fix", "refactor", "test-add", "config-change",
                "security-fix", "docs-update", "skip-low-value");
        assertTrue(categories.containsAll(required),
                "missing categories: " + required.stream().filter(r -> !categories.contains(r)).toList());

        long skipSamples = golden.samples.stream().filter(s -> !s.expectObservation).count();
        assertTrue(skipSamples >= 2, "expected at least two skip samples, got " + skipSamples);
        assertFalse(golden.samples.stream().anyMatch(s -> s.expectObservation && s.expectedType == null),
                "every observation sample must declare expectedType");
    }

    private static GoldenSet.Sample sample(boolean expectObservation, String type,
                                           List<String> titleKeywords, List<String> factsKeywords,
                                           List<String> conceptsKeywords) {
        GoldenSet.Sample sample = new GoldenSet.Sample();
        sample.id = "synthetic";
        sample.category = "synthetic";
        sample.expectObservation = expectObservation;
        sample.expectedType = type;
        sample.titleKeywords = titleKeywords;
        sample.factsKeywords = factsKeywords;
        sample.conceptsKeywords = conceptsKeywords;
        return sample;
    }

    private static XmlParser.ParsedObservation observation(String type, String title,
                                                           List<String> facts, List<String> concepts) {
        XmlParser.ParsedObservation observation = new XmlParser.ParsedObservation();
        observation.type = type;
        observation.title = title;
        observation.facts = facts;
        observation.concepts = concepts;
        return observation;
    }
}
