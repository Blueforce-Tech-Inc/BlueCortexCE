package com.ablueforce.cortexce.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for LogsController log retrieval.
 *
 * <p>Focus: the {@code totalLines} field must report the real number of lines
 * in the searched log files, not the size of the sliding window handed back to
 * the caller. The Viewer UI LogsModal only consumes {@code logs}; this field is
 * for API clients, and docs/API.md documents it as the "total line count".
 *
 * <p>No Spring context required: {@code logDir} is a plain {@code @Value} field,
 * injected here by reflection.
 */
class LogsControllerTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @TempDir
    Path logDir;

    private LogsController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new LogsController();
        setField("logDir", logDir.toString());
        // @Value defaults are not applied when the controller is constructed by hand.
        setField("logApiEnabled", true);
        setField("logApiToken", "");
    }

    private void setField(String name, Object value) throws Exception {
        Field field = LogsController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private void writeLog(int dayOffset, String... lines) throws IOException {
        Files.write(logFile(dayOffset), List.of(lines));
    }

    private Path logFile(int dayOffset) {
        String date = LocalDate.now().minusDays(dayOffset).format(DATE_FORMATTER);
        return logDir.resolve("claude-mem-" + date + ".log");
    }

    private static String[] numbered(int count) {
        String[] lines = new String[count];
        for (int i = 0; i < count; i++) {
            lines[i] = "line-" + (i + 1);
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(int lines) {
        ResponseEntity<Map<String, Object>> response = controller.getLogs(lines, null);
        assertEquals(200, response.getStatusCode().value());
        return (Map<String, Object>) response.getBody();
    }

    // --- access control: opt-in switches, default behaviour unchanged ---

    @Test
    void logApiCanBeTurnedOff() throws Exception {
        setField("logApiEnabled", false);
        var error = org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> controller.getLogs(10, null));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void clearApiCanBeTurnedOff() throws Exception {
        setField("logApiEnabled", false);
        var error = org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> controller.clearLogs(null));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void tokenIsOptionalButEnforcedWhenConfigured() throws Exception {
        setField("logApiToken", "s3cret");
        var denied = org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> controller.getLogs(10, null));
        assertEquals(403, denied.getStatusCode().value());

        var wrong = org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> controller.getLogs(10, "nope"));
        assertEquals(403, wrong.getStatusCode().value());

        // A correct token keeps the existing response shape.
        ResponseEntity<Map<String, Object>> ok = controller.getLogs(10, "s3cret");
        assertEquals(200, ok.getStatusCode().value());
    }

    private static int intOf(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).intValue();
    }

    private static List<?> listOf(Map<String, Object> body, String key) {
        return (List<?>) body.get(key);
    }

    private static String logsOf(Map<String, Object> body) {
        return (String) body.get("logs");
    }

    // --- totalLines: real file size, not window size ---

    @Test
    void totalLinesCountsWholeFileNotSlidingWindow() throws IOException {
        writeLog(0, numbered(50));

        Map<String, Object> body = call(5);

        assertEquals(50, intOf(body, "totalLines"),
                "totalLines must be the file's real line count, not the 5-line window");
        assertEquals(5, intOf(body, "returnedLines"));
        assertEquals("line-46\nline-47\nline-48\nline-49\nline-50", logsOf(body),
                "must return the LAST 5 lines");
    }

    @Test
    void totalLinesEqualsReturnedLinesWhenFileIsShorterThanWindow() throws IOException {
        writeLog(0, numbered(3));

        Map<String, Object> body = call(100);

        assertEquals(3, intOf(body, "totalLines"));
        assertEquals(3, intOf(body, "returnedLines"));
    }

    @Test
    void totalLinesSumsAcrossTodayAndYesterdayWhenFallingBack() throws IOException {
        writeLog(0, "t1", "t2");
        writeLog(1, "y1", "y2", "y3", "y4");

        // Today alone (2 lines) is below the request, so yesterday is searched too.
        Map<String, Object> body = call(100);

        assertEquals(6, intOf(body, "totalLines"), "must sum both searched files");
        assertEquals(6, intOf(body, "returnedLines"));
        assertEquals(2, listOf(body, "files").size());
    }

    @Test
    void doesNotSearchYesterdayWhenTodayAlreadySatisfiesRequest() throws IOException {
        writeLog(0, numbered(50));
        writeLog(1, numbered(40));

        Map<String, Object> body = call(5);

        assertEquals(50, intOf(body, "totalLines"),
                "yesterday must not be counted once today alone is sufficient");
        assertEquals(1, listOf(body, "files").size());
    }

    // --- window ordering across the two-day fallback ---

    @Test
    void keepsChronologicalOrderAcrossFallbackBoundary() throws IOException {
        writeLog(0, "t1", "t2");
        writeLog(1, "y1", "y2", "y3");

        Map<String, Object> body = call(10);

        assertEquals("y1\ny2\ny3\nt1\nt2", logsOf(body),
                "yesterday's entries must be prepended in chronological order");
    }

    @Test
    void returnsMostRecentLinesAcrossDayBoundary() throws IOException {
        writeLog(0, "t1", "t2");
        writeLog(1, "y1", "y2", "y3", "y4");

        // Yesterday contributes only its last 3 lines, so the window is y2..y4 + t1..t2.
        // Taking the last 3 of that keeps the boundary contiguous: y4, t1, t2.
        Map<String, Object> body = call(3);

        assertEquals("y4\nt1\nt2", logsOf(body));
        assertEquals(3, intOf(body, "returnedLines"));
        assertEquals(6, intOf(body, "totalLines"), "trimming the window must not change totalLines");
    }

    // --- clamping and empty states ---

    @Test
    void clampsLinesBelowOne() throws IOException {
        writeLog(0, numbered(10));

        Map<String, Object> body = call(0);

        assertEquals(1, intOf(body, "returnedLines"));
        assertEquals("line-10", logsOf(body));
        assertEquals(10, intOf(body, "totalLines"));
    }

    @Test
    void clampsLinesAboveMaximum() throws IOException {
        writeLog(0, numbered(10050));

        Map<String, Object> body = call(99999);

        assertEquals(10000, intOf(body, "returnedLines"));
        assertEquals(10050, intOf(body, "totalLines"));
    }

    @Test
    void reportsEmptyStateWhenNoLogFileExists() {
        Map<String, Object> body = call(100);

        assertEquals("", logsOf(body));
        assertEquals(0, intOf(body, "totalLines"));
        assertEquals(0, intOf(body, "returnedLines"));
        assertFalse((Boolean) body.get("exists"));
        assertTrue(listOf(body, "files").isEmpty());
    }

    @Test
    void reportsEmptyStateForZeroByteLogFile() throws IOException {
        Files.writeString(logFile(0), "");

        Map<String, Object> body = call(100);

        assertEquals("", logsOf(body));
        assertEquals(0, intOf(body, "totalLines"));
        assertEquals(0, intOf(body, "returnedLines"));
        assertFalse((Boolean) body.get("exists"));
        assertEquals(1, listOf(body, "files").size(),
                "an existing-but-empty file is still a searched file");
    }

    @Test
    void reportsExistsForNonEmptyResult() throws IOException {
        writeLog(0, "only line");

        Map<String, Object> body = call(100);

        assertTrue((Boolean) body.get("exists"));
    }
}
