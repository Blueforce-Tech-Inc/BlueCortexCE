package com.ablueforce.cortexce.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the mechanically generated real-history golden set.
 *
 * <p>The samples come from {@code scripts/gen-real-golden.py}; this test only verifies that the
 * generator obeyed its own rules. It never calls a model, and it is scored separately from the
 * hand-written set because the two distributions are not comparable.</p>
 */
class GoldenSetRealDataTest {

    private static final Set<String> ACTIONS =
            Set.of("create", "modify", "remove", "secure", "document", "diagnose");

    @Test
    @DisplayName("the real-history set has at least 60 mechanically valid samples")
    void realSetIsWellFormed() throws Exception {
        GoldenSet real = load("/eval/golden-tool-use-real.json");
        GoldenSet handwritten = load("/eval/golden-tool-use.json");

        assertThat(real.samples).as("real-history samples").hasSizeGreaterThanOrEqualTo(60);

        Set<String> ids = new HashSet<>();
        Set<String> handwrittenIds = new HashSet<>();
        handwritten.samples.forEach(sample -> handwrittenIds.add(sample.id));

        for (GoldenSet.Sample sample : real.samples) {
            assertThat(sample.id).as("sample id").startsWith("real-");
            assertThat(ids.add(sample.id)).as("duplicate id %s", sample.id).isTrue();
            assertThat(handwrittenIds).as("id collides with the hand-written set: %s", sample.id)
                    .doesNotContain(sample.id);
            assertThat(ACTIONS).as("action class for %s", sample.id).contains(sample.titleAction);
            assertThat(sample.expectObservation).as("observation expectation for %s", sample.id)
                    .isEqualTo(!"diagnose".equals(sample.titleAction));
            assertThat(sample.titleActions).as("action set for %s", sample.id)
                    .isNotEmpty().allMatch(ACTIONS::contains).contains(sample.titleAction);

            List<String> traceVisible = GoldenSetScorer.traceVisibleSubjectTokens(sample);
            assertThat(traceVisible).as("subject tokens must be visible in the trace of %s",
                    sample.id).isNotEmpty().containsExactlyInAnyOrderElementsOf(sample.titleSubject);

            String trace = GoldenSetScorer.traceEvidence(sample);
            for (String identifier : sample.titleIdentifiers) {
                assertThat(trace).as("identifier %s must appear in the trace of %s",
                        identifier, sample.id).contains(identifier);
            }
        }
    }

    @Test
    @DisplayName("the real-history set covers every action class with at least 10 samples")
    void realSetCoversEveryAction() throws Exception {
        GoldenSet real = load("/eval/golden-tool-use-real.json");
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (GoldenSet.Sample sample : real.samples) {
            for (String action : sample.titleActions.isEmpty()
                    ? List.of(sample.titleAction) : sample.titleActions) {
                counts.merge(action, 1, Integer::sum);
            }
        }
        assertThat(counts.keySet()).as("generated actions").containsExactlyInAnyOrderElementsOf(ACTIONS);
        counts.forEach((action, count) -> assertThat(count)
                .as("samples covering %s", action).isGreaterThanOrEqualTo(10));
        assertThat(real.samples).allSatisfy(sample -> assertThat(sample.expectedType).isNotNull());
    }

    private static GoldenSet load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = GoldenSetRealDataTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource %s", resource).isNotNull();
            return mapper.readValue(in, GoldenSet.class);
        }
    }
}
