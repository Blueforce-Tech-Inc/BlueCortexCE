package com.ablueforce.cortexce.eval;

import com.ablueforce.cortexce.util.XmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SummaryGoldenSetScorerTest {

    @Test
    @DisplayName("a clause with an invented identifier is not trace-grounded")
    void inventedIdentifierFailsGrounding() {
        String trace = "OrderQuery.pageOffset now returns page * size.";
        assertThat(SummaryGoldenSetScorer.grounded(
                "OrderQuery.pageOffset now returns page * size", trace)).isTrue();
        assertThat(SummaryGoldenSetScorer.grounded(
                "OrderQuery.pageOffset now returns page * size via BillingCache", trace)).isFalse();
    }

    @Test
    @DisplayName("support rate is clause-level, not whole-text")
    void supportRateIsClauseLevel() {
        String trace = "OrderQuery.pageOffset now returns page * size. OrderQueryTest passes.";
        double rate = SummaryGoldenSetScorer.supportRate(
                "OrderQuery.pageOffset now returns page * size. CsvExporter writes CRLF.", trace);
        assertThat(rate).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("score combines request coverage, learned recall and grounding")
    void scoreAggregates() {
        SummaryGoldenSet.Sample sample = new SummaryGoldenSet.Sample();
        sample.id = "s1";
        sample.category = "bug-fix";
        sample.requestKeywords = List.of("pagination", "offset");
        sample.learnedKeywords = List.of("page", "size");
        sample.requestIdentifiers = List.of("OrderQuery");
        sample.learnedIdentifiers = List.of("OrderQuery");
        sample.lastAssistantMessage = "Fixed OrderQuery pagination offset.";
        sample.toolTrace = "OrderQuery.pageOffset now returns page * size.";

        String raw = """
                <summary>
                  <request>Fixed OrderQuery pagination offset</request>
                  <learned>OrderQuery page * size replaces the old offset via PagingCache.</learned>
                </summary>
                """;
        XmlParser.ParsedSummary parsed = XmlParser.parseSummary(raw);
        SummaryGoldenSetScorer.Aggregate agg = SummaryGoldenSetScorer.score(List.of(
                new SummaryGoldenSetScorer.Outcome(sample, raw, parsed, false, null, 1, "200", 0,
                        false, null)));

        assertThat(agg.requestCoverage).isCloseTo(1.0, within(1e-9));
        assertThat(agg.requestIdentifierHitRate).isCloseTo(1.0, within(1e-9));
        assertThat(agg.learnedRecall).isCloseTo(1.0, within(1e-9));
        assertThat(agg.learnedIdentifierHitRate).isCloseTo(1.0, within(1e-9));
        assertThat(agg.learnedSupportRate).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("a paraphrase around a traced identifier is grounded")
    void paraphraseAroundTracedIdentifierPasses() {
        String trace = "UserCache.get now loads through computeIfAbsent, so concurrent callers"
            + " cannot both write a null placeholder.";
        // The summary says "race", which the trace never uses; the concrete object it names
        // does occur, and no invented object is introduced.
        assertThat(SummaryGoldenSetScorer.grounded("Fixed the race in UserCache", trace)).isTrue();
    }

    @Test
    @DisplayName("an invented number is not grounded even when its object is traced")
    void inventedNumberFailsGrounding() {
        String trace = "UserCache.get now loads through computeIfAbsent.";
        assertThat(SummaryGoldenSetScorer.grounded(
                "Reduced UserCache contention to 42 ms", trace)).isFalse();
    }

    @Test
    @DisplayName("a generic clause with no anchor still needs lexical support")
    void genericClaimNeedsLexicalSupport() {
        String trace = "UserCache.get now loads through computeIfAbsent.";
        assertThat(SummaryGoldenSetScorer.grounded(
                "Improved overall reliability across the board", trace)).isFalse();
    }

    @Test
    @DisplayName("format errors are counted and listed")
    void formatErrors() {
        SummaryGoldenSet.Sample sample = new SummaryGoldenSet.Sample();
        sample.id = "s2";
        SummaryGoldenSetScorer.Outcome outcome = SummaryGoldenSetEvaluator.toOutcome(
                sample, "not xml", null, "200", 2, 0);
        SummaryGoldenSetScorer.Aggregate agg = SummaryGoldenSetScorer.score(List.of(outcome));
        assertThat(agg.formatErrors).isEqualTo(1);
        assertThat(agg.problems).anyMatch(problem -> problem.contains("format error"));
    }
}
