package com.ablueforce.cortexce.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionConsistencyTest {

    private static ExtractionConsistency.Candidate parsed(String type, String title) {
        return new ExtractionConsistency.Candidate(false, true, type, title);
    }

    private static ExtractionConsistency.Candidate skipped() {
        return new ExtractionConsistency.Candidate(true, false, null, null);
    }

    private static ExtractionConsistency.Candidate unusable() {
        return new ExtractionConsistency.Candidate(false, false, null, null);
    }

    @Test
    @DisplayName("two agreeing attempts beat one disagreeing attempt")
    void majorityWins() {
        List<ExtractionConsistency.Candidate> candidates = List.of(
                parsed("bugfix", "Fixed retry loop"),
                parsed("feature", "Added retry policy"),
                parsed("bugfix", "Fixed retry loop"));

        assertThat(ExtractionConsistency.select(candidates)).isEqualTo(0);
    }

    @Test
    @DisplayName("the leading title verb participates in the signature")
    void titleLeadSeparatesSignatures() {
        List<ExtractionConsistency.Candidate> candidates = List.of(
                parsed("change", "Added cache layer"),
                parsed("feature", "Added cache layer"),
                parsed("change", "Changed cache layer"));

        assertThat(ExtractionConsistency.select(candidates)).isEqualTo(0);
    }

    @Test
    @DisplayName("skip is a vote; a majority of skips wins")
    void skipVotes() {
        List<ExtractionConsistency.Candidate> candidates = List.of(
                skipped(),
                parsed("change", "Changed logging"),
                skipped());

        assertThat(ExtractionConsistency.select(candidates)).isEqualTo(0);
    }

    @Test
    @DisplayName("unusable attempts never vote and a tie keeps the earliest answer")
    void unusableAndTies() {
        List<ExtractionConsistency.Candidate> candidates = List.of(
                unusable(),
                parsed("change", "Changed logging"),
                parsed("bugfix", "Fixed logging"));

        // Two usable answers disagree 1:1, so the earliest usable index wins.
        assertThat(ExtractionConsistency.select(candidates)).isEqualTo(1);
    }

    @Test
    @DisplayName("no usable answer returns -1")
    void nothingUsable() {
        assertThat(ExtractionConsistency.select(List.of(unusable(), unusable()))).isEqualTo(-1);
    }

    @Test
    @DisplayName("only malformed first answers trigger a repeat")
    void secondOpinionOnlyForMalformedAnswers() {
        assertThat(ExtractionConsistency.needsSecondOpinion(parsed("bugfix", "Fixed retry loop")))
                .isFalse();
        assertThat(ExtractionConsistency.needsSecondOpinion(skipped())).isFalse();

        assertThat(ExtractionConsistency.needsSecondOpinion(unusable())).isTrue();
        assertThat(ExtractionConsistency.needsSecondOpinion(parsed(null, "Fixed retry loop")))
                .isTrue();
        assertThat(ExtractionConsistency.needsSecondOpinion(parsed("bugfix", " "))).isTrue();
        assertThat(ExtractionConsistency.needsSecondOpinion(null)).isTrue();
    }
}
