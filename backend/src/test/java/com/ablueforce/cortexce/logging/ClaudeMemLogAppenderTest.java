package com.ablueforce.cortexce.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1 — the file appender rotates by size and enforces a directory cap.
 *
 * <p>Before H3 the appender wrote one unbounded {@code claude-mem-<date>.log} per day;
 * on the reference machine 105 files reached 1.5 GB. These tests drive the appender with
 * real files and a tiny limit instead of inspecting the code.
 */
class ClaudeMemLogAppenderTest {

    private static final String PREFIX = "claude-mem-";

    @TempDir
    Path tempDir;

    private final LoggerContext context = new LoggerContext();

    {
        // A bare LoggerContext has no MDC adapter, and the appender reads the MDC to
        // render the correlation id. The real runtime context always has one.
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
    }

    @Test
    void rotationKeepsPartsUnderTheLimitAndTheActiveFileNewest() throws Exception {
        ClaudeMemLogAppender appender = startAppender(4 * 1024, 1024L * 1024 * 1024);
        String line = "message-" + "x".repeat(80);
        int lines = 300;
        for (int i = 0; i < lines; i++) {
            appender.doAppend(event(line + "-" + i));
        }

        List<Path> parts;
        try (Stream<Path> stream = Files.list(tempDir)) {
            parts = sorted(stream.toList());
        }
        assertTrue(parts.size() >= 2, "expected rotation, files=" + parts);

        Path active = activeFile();
        assertTrue(Files.exists(active), "active file missing after rotation");
        String activeContent = Files.readString(active, StandardCharsets.UTF_8);
        assertTrue(activeContent.contains(line + "-" + (lines - 1)),
            "the newest line must be in the date-named active file");

        for (Path part : parts) {
            long size = Files.size(part);
            // A part may exceed the limit by at most the one line that triggered rotation.
            assertTrue(size <= 4 * 1024 + 200, part + " is " + size + " bytes");
        }
    }

    @Test
    void cleanupDeletesOldestPartsUntilTheDirectoryFitsTheCap() throws Exception {
        Path oldest = tempDir.resolve(PREFIX + "2020-01-01.log");
        Path middle = tempDir.resolve(PREFIX + "2020-06-01.log");
        Path newest = tempDir.resolve(PREFIX + "2020-12-01.log");
        Files.writeString(oldest, "a".repeat(10 * 1024));
        Files.writeString(middle, "b".repeat(10 * 1024));
        Files.writeString(newest, "c".repeat(10 * 1024));
        Files.setLastModifiedTime(oldest, FileTime.fromMillis(1_000_000_000_000L));
        Files.setLastModifiedTime(middle, FileTime.fromMillis(1_100_000_000_000L));
        Files.setLastModifiedTime(newest, FileTime.fromMillis(1_200_000_000_000L));
        Path unrelated = tempDir.resolve("other-app.log");
        Files.writeString(unrelated, "keep me");

        ClaudeMemLogAppender appender = startAppender(4 * 1024, 20 * 1024);
        appender.doAppend(event("today"));

        assertFalse(Files.exists(oldest), "the oldest part must be deleted first");
        assertTrue(Files.exists(middle), "middle part still fits the cap");
        assertTrue(Files.exists(newest), "newest part must be kept");
        assertTrue(Files.exists(unrelated), "files without the claude-mem prefix are untouched");
        assertTrue(Files.exists(activeFile()), "the active file must never be deleted");
    }

    @Test
    void aLineLargerThanTheLimitIsStillWritten() throws Exception {
        ClaudeMemLogAppender appender = startAppender(1024, 1024L * 1024 * 1024);
        String huge = "single-line-" + "y".repeat(2048);
        appender.doAppend(event(huge));

        String content = Files.readString(activeFile(), StandardCharsets.UTF_8);
        assertTrue(content.contains(huge), "an oversized line must not be dropped");
        assertEquals(1, countLogFiles());
    }

    private ClaudeMemLogAppender startAppender(long maxFileSizeBytes, long totalSizeCapBytes) {
        ClaudeMemLogAppender appender = new ClaudeMemLogAppender();
        appender.setContext(context);
        appender.setLogDir(tempDir.toString());
        appender.setMaxFileSize(FileSize.valueOf(String.valueOf(maxFileSizeBytes)));
        appender.setTotalSizeCap(FileSize.valueOf(String.valueOf(totalSizeCapBytes)));
        appender.start();
        return appender;
    }

    private ILoggingEvent event(String message) {
        Logger logger = context.getLogger("com.ablueforce.cortexce.test");
        return new LoggingEvent("com.ablueforce.cortexce.test", logger, Level.INFO, message, null, null);
    }

    private Path activeFile() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return tempDir.resolve(PREFIX + date + ".log");
    }

    private long countLogFiles() throws Exception {
        try (Stream<Path> stream = Files.list(tempDir)) {
            return stream.filter(path -> path.getFileName().toString().startsWith(PREFIX)).count();
        }
    }

    private static List<Path> sorted(List<Path> paths) {
        return paths.stream().sorted().toList();
    }
}
