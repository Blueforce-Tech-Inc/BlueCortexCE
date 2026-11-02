package com.ablueforce.cortexce.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural checks on the expanded golden set. Offline only. */
class GoldenSetDataTest {

    private static final Set<String> REQUIRED_CATEGORIES = Set.of(
            "bug-fix", "refactor", "test-add", "security-fix",
            "config-change", "docs-update", "deploy-change", "skip-low-value");

    private static final Set<String> ALLOWED_CONCEPTS = Set.of(
            "how-it-works", "why-it-exists", "what-changed", "problem-solution",
            "gotcha", "pattern", "trade-off");

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "bugfix", "feature", "refactor", "change", "discovery", "decision");

    @Test
    @DisplayName("golden set size, ids and category coverage")
    void sizeAndCategories() throws Exception {
        GoldenSet golden = load();

        assertThat(golden.samples.size()).isBetween(60, 100);
        assertThat(golden.samples).extracting(sample -> sample.id)
                .doesNotHaveDuplicates();
        assertThat(golden.samples).extracting(sample -> sample.category)
                .containsAll(REQUIRED_CATEGORIES);
        assertThat(golden.samples).filteredOn(sample -> sample.keySample).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("every observation sample declares an achievable title, type and field contract")
    void observationContracts() throws Exception {
        GoldenSet golden = load();

        for (GoldenSet.Sample sample : golden.samples) {
            if (!sample.expectObservation) {
                assertThat(sample.expectedType).as(sample.id).isNull();
                assertThat(sample.titleKeywords).as(sample.id).isEmpty();
                assertThat(sample.titleVerb).as(sample.id).isNull();
                assertThat(sample.titleSubject).as(sample.id).isEmpty();
                assertThat(sample.titleIdentifiers).as(sample.id).isEmpty();
                continue;
            }
            assertThat(sample.expectedType).as(sample.id).isIn(ALLOWED_TYPES);
            assertThat(sample.titleKeywords).as(sample.id).isNotEmpty();
            assertThat(sample.titleVerb).as(sample.id)
                    .isNotBlank()
                    .isIn(GoldenSetScorer.VERB_CLASSES.keySet());
            assertThat(sample.titleSubject).as(sample.id).isNotEmpty();
            assertThat(sample.titleIdentifiers).as(sample.id).isNotEmpty();
            String titleContract = sample.titleVerb + " " + String.join(" ", sample.titleSubject)
                    + " " + String.join(" ", sample.titleIdentifiers);
            assertThat(titleContract).as(sample.id + " contract tokens")
                    .doesNotContainIgnoringCase("null");
            assertThat(sample.factsKeywords).as(sample.id).isNotEmpty();
            assertThat(sample.conceptsKeywords).as(sample.id)
                    .isNotEmpty().allMatch(ALLOWED_CONCEPTS::contains);

            String trace = sample.toolName + "\n" + sample.toolInput + "\n" + sample.toolOutput;
            assertThat(trace).as(sample.id + " trace")
                    .contains(sample.toolName).contains(sample.toolInput).contains(sample.toolOutput);
            for (String identifier : sample.titleIdentifiers) {
                assertThat(trace)
                        .as(sample.id + " must contain identifier " + identifier + " verbatim")
                        .contains(identifier);
            }
        }
    }

    @Test
    @DisplayName("action labels are the six-enum values and match the trace-derived class")
    void actionLabelsCannotDrift() throws Exception {
        GoldenSet golden = load();

        long unclassifiable = 0;
        for (GoldenSet.Sample sample : golden.samples) {
            assertThat(sample.titleAction).as(sample.id + " action label").isNotNull();
            if (GoldenSetActionRules.UNCLASSIFIABLE.equals(sample.titleAction)) {
                unclassifiable++;
                continue;
            }
            assertThat(sample.titleAction).as(sample.id + " action label")
                    .isIn(GoldenSetActionRules.ACTIONS);
            assertThat(sample.titleAction)
                    .as(sample.id + " label must equal the mechanical derivation; edit the rule, "
                            + "not the label")
                    .isEqualTo(GoldenSetActionRules.derive(sample));
        }
        assertThat(unclassifiable / (double) golden.samples.size())
                .as("unclassifiable share of the golden set")
                .isLessThanOrEqualTo(0.10);
    }

    @Test
    @DisplayName("skip samples cover pure read, listing, status, multi-step and no-conclusion forms")
    void skipCoverage() throws Exception {
        GoldenSet golden = load();

        List<String> notes = golden.samples.stream()
                .filter(sample -> !sample.expectObservation)
                .map(sample -> sample.notes == null ? "" : sample.notes)
                .collect(Collectors.toList());

        assertThat(notes).hasSizeGreaterThanOrEqualTo(15);
        assertThat(notes).anySatisfy(note -> assertThat(note).contains("pure read"));
        assertThat(notes).anySatisfy(note -> assertThat(note).contains("listing"));
        assertThat(notes).anySatisfy(note -> assertThat(note).contains("multi-step"));
        assertThat(notes).anySatisfy(note -> assertThat(note).contains("no conclusion"));
    }

    @Test
    @DisplayName("late samples exercise short, long and multi-call traces")
    void traceShapeCoverage() throws Exception {
        GoldenSet golden = load();

        long longTraces = golden.samples.stream()
                .filter(sample -> (sample.toolInput + sample.toolOutput).length() > 300)
                .count();
        Set<String> tools = new HashSet<>(golden.samples.stream()
                .map(sample -> sample.toolName).collect(Collectors.toSet()));

        assertThat(longTraces).isGreaterThanOrEqualTo(10);
        assertThat(tools).contains("Read", "Edit", "Write", "Grep", "Glob", "Shell");
    }

    private static GoldenSet load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = GoldenSetDataTest.class.getResourceAsStream("/eval/golden-tool-use.json")) {
            assertThat(in).isNotNull();
            Map<?, ?> ignored = mapper.readValue(in, Map.class);
            assertThat(ignored.get("samples")).isNotNull();
        }
        try (InputStream in = GoldenSetDataTest.class.getResourceAsStream("/eval/golden-tool-use.json")) {
            return mapper.readValue(in, GoldenSet.class);
        }
    }
}
