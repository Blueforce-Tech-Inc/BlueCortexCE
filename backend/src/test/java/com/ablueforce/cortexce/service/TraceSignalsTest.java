package com.ablueforce.cortexce.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceSignalsTest {

    @Test
    void testOnlyChangeIsReportedAsTestsOnly() {
        String input = "file_path=backend/src/test/java/com/ablueforce/cortexce/service/FooTest.java";
        String output = "Edit file: removed 3 lines, added 24 lines";
        TraceSignals.Signals signals = TraceSignals.of(input, output);
        assertEquals(1, signals.testFiles());
        assertEquals(0, signals.sourceFiles());
        assertTrue(signals.testsOnly());
        assertFalse(signals.docsOnly());
    }

    @Test
    void docsOnlyChangeIsReportedAsDocsOnly() {
        TraceSignals.Signals signals = TraceSignals.of("docs/API.md docs/API-zh-CN.md", "Edit file");
        assertEquals(2, signals.docFiles());
        assertTrue(signals.docsOnly());
    }

    @Test
    void configOnlyChangeIsReportedAsConfigOnly() {
        TraceSignals.Signals signals = TraceSignals.of(
                "backend/src/main/resources/application.yml", "Edit file: removed 1 lines, added 2 lines");
        assertTrue(signals.configOnly());
        assertFalse(signals.testsOnly());
    }

    @Test
    void mixedSourceAndTestChangeIsNeitherTestsOnlyNorConfigOnly() {
        TraceSignals.Signals signals = TraceSignals.of(
                "backend/src/main/java/com/ablueforce/cortexce/service/Foo.java "
                        + "backend/src/test/java/com/ablueforce/cortexce/service/FooTest.java",
                "Edit file");
        assertEquals(1, signals.sourceFiles());
        assertEquals(1, signals.testFiles());
        assertFalse(signals.testsOnly());
        assertFalse(signals.configOnly());
    }

    @Test
    void securityTextAndPublicApiAreDetectedWithoutBeingALabel() {
        TraceSignals.Signals signals = TraceSignals.of(
                "backend/src/main/java/com/ablueforce/cortexce/service/TokenService.java",
                "Edit file\n+ public String issueToken(String subject) {");
        assertTrue(signals.securityRelated());
        assertTrue(signals.addsPublicApi());
        String block = TraceSignals.hintBlock(
                "backend/src/main/java/com/ablueforce/cortexce/service/TokenService.java",
                "Edit file\n+ public String issueToken(String subject) {");
        assertFalse(block.contains("<type>"), "the hint block must never carry a type label");
    }

    @Test
    void defectEvidenceIsDetectedFromTraceWording() {
        assertTrue(TraceSignals.of(
                "backend/src/main/java/.../Foo.java",
                "Fix the null check that crashed on empty input").defectEvidence());
        assertFalse(TraceSignals.of(
                "backend/src/main/java/.../Foo.java",
                "Add a new endpoint returning the current count").defectEvidence());
    }

    @Test
    void addedProseAboutABugIsNotDefectEvidence() {
        String output = "Edit backend/src/test/java/Foo.java: removed 1 line(s), added 2 line(s); "
                + "'int x = 1;' -> '// this is the bug it reproduces'";
        assertFalse(TraceSignals.of("file_path=backend/src/test/java/Foo.java", output)
                .defectEvidence());
    }

    @Test
    void removedDefectWordingIsEvidence() {
        String output = "Edit backend/src/main/java/Foo.java: removed 1 line(s), added 1 line(s); "
                + "'catch (Exception e) { return null; }' -> 'return value;'";
        assertTrue(TraceSignals.of("file_path=backend/src/main/java/Foo.java", output)
                .defectEvidence());
    }

    @Test
    void renameIsDetected() {
        TraceSignals.Signals signals = TraceSignals.of("provider/ModeService.java", "renamed ModeService to MemoryModeService");
        assertTrue(signals.renameOrMove());
    }

    @Test
    void extensionlessBuildFilesCountAsConfig() {
        TraceSignals.Signals signals = TraceSignals.of(
                "backend/Dockerfile docs/DEPLOYMENT.md",
                "Edit backend/Dockerfile: removed 3 line(s), added 3 line(s)");
        assertEquals(2, signals.files());
        assertEquals(1, signals.configFiles());
        assertEquals(1, signals.docFiles());
        assertFalse(signals.docsOnly(), "a Dockerfile edit must not look docs-only");
        assertFalse(signals.configOnly());
    }

    @Test
    void candidateArtifactsAreRankedByChangedLines() {
        String input = "file_path=backend/src/main/java/com/ablueforce/cortexce/service/Foo.java\n"
                + "read_files=backend/src/test/java/com/ablueforce/cortexce/service/FooTest.java";
        String output = "Edit backend/src/main/java/com/ablueforce/cortexce/service/Foo.java: "
                + "removed 2 line(s), added 3 line(s); Edit "
                + "backend/src/test/java/com/ablueforce/cortexce/service/FooTest.java: "
                + "removed 10 line(s), added 40 line(s)";
        TraceSignals.Signals signals = TraceSignals.of(input, output);
        assertEquals(2, signals.artifacts().size());
        assertEquals("backend/src/test/java/com/ablueforce/cortexce/service/FooTest.java",
                signals.artifacts().get(0).path());
        assertEquals(50, signals.artifacts().get(0).changedLines());
        assertEquals(5, signals.artifacts().get(1).changedLines());
    }

    @Test
    void extensionlessArtifactKeepsItsEditStatOrderOnTies() {
        String input = "file_path=backend/Dockerfile\n"
                + "read_files=backend/Dockerfile, docs/DEPLOYMENT-zh-CN.md";
        String output = "Edit backend/Dockerfile: removed 3 line(s), added 3 line(s); "
                + "'# port' -> '# port 37777' Edit docs/DEPLOYMENT-zh-CN.md: "
                + "removed 6 line(s), added 0 line(s); '# build' -> '(none)'";
        TraceSignals.Signals signals = TraceSignals.of(input, output);
        assertEquals("backend/Dockerfile", signals.artifacts().get(0).path());
        assertEquals(6, signals.artifacts().get(0).changedLines());
        assertEquals(6, signals.artifacts().get(1).changedLines());
    }

    @Test
    void candidateIdentifiersCarryPathAndBasenameWithoutJavaSuffix() {
        String input = "file_path=backend/src/main/java/com/ablueforce/cortexce/service/GoldenSetScorer.java\n"
                + "read_files=docs/API.md";
        String output = "Edit backend/src/main/java/com/ablueforce/cortexce/service/GoldenSetScorer.java: "
                + "removed 1 line(s), added 9 line(s)";
        TraceSignals.Signals signals = TraceSignals.of(input, output);
        assertTrue(signals.identifiers().contains(
                "backend/src/main/java/com/ablueforce/cortexce/service/GoldenSetScorer.java"));
        assertTrue(signals.identifiers().contains("GoldenSetScorer"));
        assertEquals("GoldenSetScorer", signals.identifiers().get(1));
        String block = TraceSignals.hintBlock(input, output);
        // Both lists are comma-separated single lines: a supporting file may appear in
        // <files_touched>, but it must not spend tokens inside either candidate list.
        String artifacts = between(block, "candidate_artifacts");
        String candidates = between(block, "candidate_identifiers");
        assertFalse(artifacts.contains("<artifact"));
        assertTrue(artifacts.contains(" ["));
        assertFalse(candidates.contains("<identifier>"));
        assertFalse(candidates.contains("API.md"));
        assertTrue(candidates.contains("GoldenSetScorer"));
        assertTrue(block.contains(
                "<title_subject_candidate>backend/src/main/java/com/ablueforce/cortexce/service/"
                        + "GoldenSetScorer.java</title_subject_candidate>"));
        assertFalse(block.contains("<title_identifier_candidate>"));
        assertTrue(block.contains("<title_contract>The title MUST end with GoldenSetScorer"));
        assertTrue(block.contains("<title_identifier_required>GoldenSetScorer</title_identifier_required>"));
        assertTrue(block.contains(
                "<title_budget identifier_chars=\"15\" verb_object_chars=\"85\"/>"));
        assertFalse(block.contains("<type>"));
    }

    @Test
    void titleBudgetLeavesRoomForTheLongestIdentifier() {
        String path = "docs/2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md";
        String input = "file_path=" + path;
        String output = "Edit " + path + ": removed 0 line(s), added 12 line(s)";
        String block = TraceSignals.hintBlock(input, output);
        String identifier = "2026-11-03_model-output-quality-eval-minimax-after-v4-contract.md";
        int expected = Math.max(0, 100 - identifier.length());
        assertTrue(block.contains("identifier_chars=\"" + identifier.length() + "\""));
        assertTrue(block.contains("verb_object_chars=\"" + expected + "\""));
        assertTrue(block.contains("<title_identifier_required>" + identifier
                + "</title_identifier_required>"));
    }

    @Test
    void candidatesIncludeSymbolsConfigKeysAndEndpoints() {
        String text = "backend/src/main/java/com/ablueforce/cortexce/service/TokenService.java\n"
                + "application.yml\n"
                + "+ public class TokenService {\n"
                + "+ public String issueToken(String subject) {\n"
                + "server.port: 37777\n"
                + "+\"/api/health\"";
        TraceSignals.Signals signals = TraceSignals.of(text, "");
        assertTrue(signals.identifiers().contains("TokenService"));
        assertTrue(signals.identifiers().contains("issueToken"));
        assertTrue(signals.identifiers().contains("server.port"));
        assertTrue(signals.identifiers().contains("/api/health"));
    }

    @Test
    void emptyTraceProducesZeroCounts() {
        TraceSignals.Signals signals = TraceSignals.of("", "");
        assertEquals(0, signals.files());
        assertFalse(signals.docsOnly());
        assertFalse(signals.configOnly());
    }

    /** Body of {@code <tag>…</tag>}, so an assertion cannot match a neighbouring block. */
    private static String between(String block, String tag) {
        int start = block.indexOf("<" + tag + ">");
        int end = block.indexOf("</" + tag + ">", start);
        assertTrue(start >= 0 && end > start, "missing <" + tag + "> block");
        return block.substring(start, end);
    }
}
