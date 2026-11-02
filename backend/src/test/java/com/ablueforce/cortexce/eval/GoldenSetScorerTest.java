package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.util.XmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Pure scoring tests: no provider call, no Spring context. */
class GoldenSetScorerTest {

    @Test
    @DisplayName("normalized title matching accepts CamelCase, punctuation and synonyms")
    void normalizedTitleMatching() {
        GoldenSet.Sample sample = sample("bug-fix", true, "bugfix",
                List.of("fix", "pagination", "orderquery"), List.of("OrderQuery"));

        GoldenSetScorer.SampleScore exact = scoreOne(sample,
                observation("bugfix", "Fixed pagination offset in OrderQuery"));
        assertThat(exact.titleExactHit).isTrue();
        assertThat(exact.titleNormalizedHit).isTrue();

        GoldenSetScorer.SampleScore paraphrase = scoreOne(sample,
                observation("bugfix", "Repaired pagination bug in OrderQuery!"));
        assertThat(paraphrase.titleExactHit).isFalse();
        assertThat(paraphrase.titleNormalizedHit).isTrue();
        assertThat(paraphrase.titleCoverage).isEqualTo(1.0);
    }

    @Test
    @DisplayName("identifiers must keep their case; a lowercased identifier is not a hit")
    void identifiersMustBeVerbatim() {
        GoldenSet.Sample sample = sample("bug-fix", true, "bugfix",
                List.of("fix", "pagination", "orderquery"), List.of("OrderQuery"));

        GoldenSetScorer.SampleScore score = scoreOne(sample,
                observation("bugfix", "Fixed pagination offset in ORDERQUERY"));

        assertThat(score.titleIdentifierHit).isFalse();
        assertThat(score.titleNormalizedHit).isFalse();
    }

    @Test
    @DisplayName("skip confusion matrix and contract pass rate")
    void skipConfusionMatrix() {
        GoldenSet.Sample record = sample("bug-fix", true, "bugfix", List.of("fix"), List.of());
        GoldenSet.Sample skip = sample("skip-low-value", false, null, List.of(), List.of());

        List<GoldenSetScorer.Outcome> outcomes = List.of(
                outcome(skip, true, null),
                outcome(skip, false, observation("discovery", "Something")),
                outcome(record, true, null),
                outcome(record, false, observation("bugfix", "Fixed thing")));

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(outcomes);

        assertThat(agg.total).isEqualTo(4);
        assertThat(agg.skipTp).isEqualTo(1);
        assertThat(agg.skipFn).isEqualTo(1);
        assertThat(agg.skipFp).isEqualTo(1);
        assertThat(agg.skipTn).isEqualTo(1);
        assertThat(agg.skipAccuracy).isEqualTo(0.5);
        assertThat(agg.contractPassRate).isEqualTo(0.5);
    }

    @Test
    @DisplayName("malformed answers count as format errors, not as observations")
    void formatErrorsCounted() {
        GoldenSet.Sample record = sample("bug-fix", true, "bugfix", List.of("fix"), List.of());
        GoldenSetScorer.Outcome bad = new GoldenSetScorer.Outcome(record, "not xml", false,
                null, false, "No <observation> block found in XML", 100);

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(bad));

        assertThat(agg.formatErrors).isEqualTo(1);
        assertThat(agg.contractPassRate).isZero();
        assertThat(agg.problems).anySatisfy(problem -> assertThat(problem).contains("format error"));
    }

    @Test
    @DisplayName("repeat runs report per-run metrics and skip-decision consistency")
    void repeatRuns() {
        GoldenSet.Sample skip = sample("skip-low-value", false, null, List.of(), List.of());
        List<GoldenSetScorer.Outcome> outcomes = List.of(
                outcomeWithRun(skip, true, null, 0),
                outcomeWithRun(skip, true, null, 1),
                outcomeWithRun(skip, false, observation("discovery", "Noise"), 2));

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(outcomes);

        assertThat(agg.repeatedSamples).isEqualTo(1);
        assertThat(agg.repeatConsistency).isCloseTo(0.0, within(1e-9));
        assertThat(agg.runs).hasSize(3);
        assertThat(agg.runs).extracting(run -> run.runIndex).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("per-type accuracy is reported for every category")
    void perTypeAccuracy() {
        GoldenSet.Sample bug = sample("bug-fix", true, "bugfix", List.of("fix"), List.of());
        GoldenSet.Sample skip = sample("skip-low-value", false, null, List.of(), List.of());

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(
                outcome(bug, false, observation("bugfix", "Fixed bug")),
                outcome(skip, true, null)));

        assertThat(agg.perType.get("bug-fix").samples).isEqualTo(1);
        assertThat(agg.perType.get("bug-fix").typeMatch).isEqualTo(1);
        assertThat(agg.perType.get("skip-low-value").contractPass).isEqualTo(1);
    }

    @Test
    @DisplayName("status distribution and latency percentiles are collected")
    void statusAndLatency() {
        GoldenSet.Sample record = sample("bug-fix", true, "bugfix", List.of("fix"), List.of());

        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(
                new GoldenSetScorer.Outcome(record, null, false, observation("bugfix", "Fixed bug"),
                        false, null, 10, "200", 0, false),
                new GoldenSetScorer.Outcome(record, null, false, observation("bugfix", "Fixed bug"),
                        false, null, 20, "200", 0, false),
                new GoldenSetScorer.Outcome(record, null, false, null, true,
                        "HTTP 429", 5, "429", 0, false)));

        assertThat(agg.statusCounts).containsEntry("200", 2).containsEntry("429", 1);
        assertThat(agg.callFailures).isEqualTo(1);
        assertThat(agg.p50Ms).isEqualTo(10);
        assertThat(agg.maxMs).isEqualTo(20);
    }

    @Test
    @DisplayName("facts support rate is measured against the recorded trace")
    void factsSupportRate() {
        GoldenSet.Sample sample = sample("bug-fix", true, "bugfix", List.of("fix"), List.of());
        sample.toolName = "Edit";
        sample.toolInput = "file_path=PaymentService.java";
        sample.toolOutput = "PaymentService throws PaymentNotFoundException for a missing orderId.";

        double rate = GoldenSetScorer.supportRate(List.of(
                "PaymentService throws PaymentNotFoundException for a missing orderId",
                "Telemetry dashboards were redesigned"), sample);

        assertThat(rate).isEqualTo(0.5);
    }

    @Test
    @DisplayName("keyword precision and recall ignore morphology and punctuation")
    void keywordPrecisionAndRecall() {
        assertThat(GoldenSetScorer.recall(List.of("PaymentNotFoundException"),
                List.of("throws payment-not-found-exception"))).isEqualTo(1.0);
        assertThat(GoldenSetScorer.precision(List.of("pagination"),
                List.of("Fixed pagination off-by-one", "updated the readme"))).isEqualTo(0.5);
    }

    private static GoldenSetScorer.SampleScore scoreOne(GoldenSet.Sample sample,
                                                        XmlParser.ParsedObservation observation) {
        GoldenSetScorer.Aggregate agg = GoldenSetScorer.score(List.of(outcome(sample, false, observation)));
        return agg.scores.get(0);
    }

    private static GoldenSetScorer.Outcome outcome(GoldenSet.Sample sample, boolean skipped,
                                                   XmlParser.ParsedObservation observation) {
        return outcomeWithRun(sample, skipped, observation, 0);
    }

    private static GoldenSetScorer.Outcome outcomeWithRun(GoldenSet.Sample sample, boolean skipped,
                                                          XmlParser.ParsedObservation observation,
                                                          int runIndex) {
        return new GoldenSetScorer.Outcome(sample, "raw", skipped, observation, false, null,
                100, "200", runIndex, false);
    }

    private static GoldenSet.Sample sample(String category, boolean expectObservation,
                                           String expectedType, List<String> titleKeywords,
                                           List<String> titleIdentifiers) {
        GoldenSet.Sample sample = new GoldenSet.Sample();
        sample.id = "sample-" + category;
        sample.category = category;
        sample.expectObservation = expectObservation;
        sample.expectedType = expectedType;
        sample.titleKeywords = titleKeywords;
        sample.titleIdentifiers = titleIdentifiers;
        sample.factsKeywords = List.of();
        sample.conceptsKeywords = List.of();
        return sample;
    }

    private static XmlParser.ParsedObservation observation(String type, String title) {
        XmlParser.ParsedObservation parsed = new XmlParser.ParsedObservation();
        parsed.type = type;
        parsed.title = title;
        parsed.facts = List.of("a");
        parsed.concepts = List.of("what-changed");
        return parsed;
    }
}
