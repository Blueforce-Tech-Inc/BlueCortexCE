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
    void emptyTraceProducesZeroCounts() {
        TraceSignals.Signals signals = TraceSignals.of("", "");
        assertEquals(0, signals.files());
        assertFalse(signals.docsOnly());
        assertFalse(signals.configOnly());
    }
}
