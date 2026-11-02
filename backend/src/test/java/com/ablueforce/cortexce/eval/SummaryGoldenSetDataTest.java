package com.ablueforce.cortexce.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural checks on the summary golden set: size, uniqueness, and the rule that every declared
 * expected keyword/identifier is demonstrably present in the recorded evidence. This is what stops
 * the fixtures themselves from encoding an unsupported expectation.
 */
class SummaryGoldenSetDataTest {

    @Test
    @DisplayName("summary golden set has 40+ unique, fully declared samples")
    void sizeAndShape() throws Exception {
        SummaryGoldenSet golden = SummaryGoldenSetEvaluator.loadGoldenSet();
        assertThat(golden.version).isNotBlank();
        assertThat(golden.samples).hasSizeGreaterThanOrEqualTo(40);
        Set<String> ids = new HashSet<>();
        for (SummaryGoldenSet.Sample sample : golden.samples) {
            assertThat(ids.add(sample.id)).as("duplicate id " + sample.id).isTrue();
            assertThat(sample.category).as(sample.id).isNotBlank();
            assertThat(sample.lastAssistantMessage).as(sample.id).isNotBlank();
            assertThat(sample.toolTrace).as(sample.id).isNotBlank();
            assertThat(sample.observations).as(sample.id).isNotEmpty();
            assertThat(sample.requestKeywords).as(sample.id).isNotEmpty();
            assertThat(sample.learnedKeywords).as(sample.id).isNotEmpty();
            assertThat(sample.requestIdentifiers).as(sample.id).isNotEmpty();
            assertThat(sample.learnedIdentifiers).as(sample.id).isNotEmpty();
        }
    }

    @Test
    @DisplayName("every expected keyword and identifier is present in the recorded evidence")
    void expectationsAreTraceBacked() throws Exception {
        SummaryGoldenSet golden = SummaryGoldenSetEvaluator.loadGoldenSet();
        for (SummaryGoldenSet.Sample sample : golden.samples) {
            String requestEvidence = sample.lastAssistantMessage + "\n" + sample.toolTrace;
            String learnedEvidence = sample.toolTrace;
            for (String keyword : sample.requestKeywords) {
                assertThat(containsIgnoreCase(requestEvidence, keyword))
                        .as(sample.id + " request keyword not in evidence: " + keyword).isTrue();
            }
            for (String keyword : sample.learnedKeywords) {
                assertThat(containsIgnoreCase(learnedEvidence, keyword))
                        .as(sample.id + " learned keyword not in trace: " + keyword).isTrue();
            }
            for (String identifier : sample.requestIdentifiers) {
                assertThat(requestEvidence.contains(identifier))
                        .as(sample.id + " request identifier not in evidence: " + identifier).isTrue();
            }
            for (String identifier : sample.learnedIdentifiers) {
                assertThat(learnedEvidence.contains(identifier))
                        .as(sample.id + " learned identifier not in trace: " + identifier).isTrue();
            }
        }
    }

    @Test
    @DisplayName("digest mirrors the production observation format")
    void digestShape() throws Exception {
        SummaryGoldenSet golden = SummaryGoldenSetEvaluator.loadGoldenSet();
        SummaryGoldenSet.Sample sample = golden.samples.get(0);
        String digest = SummaryGoldenSet.digest(sample);
        assertThat(digest).contains("[1] " + sample.observations.get(0).type);
        assertThat(digest).contains("  * ");
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
