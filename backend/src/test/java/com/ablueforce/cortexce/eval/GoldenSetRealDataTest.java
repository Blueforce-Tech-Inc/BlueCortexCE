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
    @DisplayName("the real-history set has at least 30 mechanically valid samples")
    void realSetIsWellFormed() throws Exception {
        GoldenSet real = load("/eval/golden-tool-use-real.json");
        GoldenSet handwritten = load("/eval/golden-tool-use.json");

        assertThat(real.samples).as("real-history samples").hasSizeGreaterThanOrEqualTo(30);

        Set<String> ids = new HashSet<>();
        Set<String> handwrittenIds = new HashSet<>();
        handwritten.samples.forEach(sample -> handwrittenIds.add(sample.id));

        for (GoldenSet.Sample sample : real.samples) {
            assertThat(sample.id).as("sample id").startsWith("real-");
            assertThat(ids.add(sample.id)).as("duplicate id %s", sample.id).isTrue();
            assertThat(handwrittenIds).as("id collides with the hand-written set: %s", sample.id)
                    .doesNotContain(sample.id);
            assertThat(ACTIONS).as("action class for %s", sample.id).contains(sample.titleAction);
            assertThat(sample.expectObservation).as("observation expected for %s", sample.id)
                    .isTrue();

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
    @DisplayName("the real-history set covers more than one action class")
    void realSetCoversSeveralActions() throws Exception {
        GoldenSet real = load("/eval/golden-tool-use-real.json");
        Set<String> actions = new HashSet<>();
        List<String> types = new ArrayList<>();
        for (GoldenSet.Sample sample : real.samples) {
            actions.add(sample.titleAction);
            types.add(sample.expectedType);
        }
        assertThat(actions).as("generated actions").hasSizeGreaterThanOrEqualTo(2);
        assertThat(types).as("generated types").doesNotContainNull();
    }

    private static GoldenSet load(String resource) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = GoldenSetRealDataTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("resource %s", resource).isNotNull();
            return mapper.readValue(in, GoldenSet.class);
        }
    }
}
