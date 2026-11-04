package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.RetentionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the retention job's safety boundaries: 0 = keep forever,
 * dry-run never writes, the cutoff is strict, and a scoped run carries the
 * project predicate so it cannot reach another project's rows.
 */
class RetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T05:00:00Z");

    @TempDir
    Path logDir;

    private NamedParameterJdbcTemplate jdbc;
    private RetentionProperties properties;
    private RetentionService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        properties = new RetentionProperties();
        service = new RetentionService(jdbc, properties);
    }

    @Test
    @DisplayName("every window at 0 (default) keeps everything and touches nothing")
    void zeroWindowsKeepEverything() {
        RetentionService.RetentionReport report = service.run(NOW, false, null);

        assertEquals(0, report.databaseRows());
        assertEquals(0, report.logFiles());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("negative or zero windows are treated as disabled")
    void negativeWindowIsDisabled() {
        properties.setObservationsDays(-1);

        RetentionService.RetentionReport report = service.run(NOW, false, null);

        assertEquals(0, report.observations());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("dry-run counts the expired rows and never issues a DELETE")
    void dryRunCountsButDoesNotDelete() {
        properties.setObservationsDays(30);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
            .thenReturn(4L);

        RetentionService.RetentionReport report = service.run(NOW, true, null);

        assertEquals(4, report.observations());
        assertTrue(report.dryRun());
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @DisplayName("real run deletes with a strict cutoff, and a scoped run carries project_path")
    void realScopedRunUsesStrictCutoffAndProjectPredicate() {
        properties.setObservationsDays(30);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
            .thenReturn(3L);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(3);

        RetentionService.RetentionReport report = service.run(NOW, false, "proj-a");

        assertEquals(3, report.observations());
        assertEquals("proj-a", report.project());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        ArgumentCaptor<String> deleteSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(deleteSql.capture(), params.capture());

        String sql = deleteSql.getValue();
        assertTrue(sql.startsWith("DELETE FROM mem_observations WHERE created_at_epoch < :cutoff"),
            "cutoff must be strict so a row on the boundary is kept: " + sql);
        assertTrue(sql.contains("AND project_path = :project"),
            "a scoped run must filter by project: " + sql);

        Map<String, ?> values = ((MapSqlParameterSource) params.getValue()).getValues();
        assertEquals("proj-a", values.get("project"));
        assertEquals(NOW.minus(Duration.ofDays(30)).toEpochMilli(), values.get("cutoff"));
    }

    @Test
    @DisplayName("an unscoped run has no project predicate")
    void unscopedRunHasNoProjectFilter() {
        properties.setPromptsDays(30);
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
            .thenReturn(0L);

        service.run(NOW, false, "");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(SqlParameterSource.class), eq(Long.class));
        assertFalse(sql.getValue().contains("project_path"),
            "blank scope must not filter on project: " + sql.getValue());
    }

    @Test
    @DisplayName("log cleanup keeps the active log and unrelated files, and deletes only old rolled files")
    void logCleanupOnlyDeletesOldRolledFiles() throws IOException {
        properties.setLogsDays(7);
        properties.setLogDir(logDir.toString());

        Path active = createLog("claude-mem.log", NOW.minus(Duration.ofMinutes(1)).toEpochMilli());
        Path old = createLog("claude-mem.2026-10-01.0.log", NOW.minus(Duration.ofDays(10)).toEpochMilli());
        Path unrelated = createLog("other-app.log", NOW.minus(Duration.ofDays(10)).toEpochMilli());

        RetentionService.RetentionReport report = service.run(NOW, false, null);

        assertEquals(1, report.logFiles());
        assertFalse(Files.exists(old), "expired rolled log should be deleted");
        assertTrue(Files.exists(active), "the active log is newer than the cutoff and must stay");
        assertTrue(Files.exists(unrelated), "files without the configured prefix must never be touched");
    }

    @Test
    @DisplayName("log dry-run reports the expired file but leaves it on disk")
    void logDryRunKeepsFile() throws IOException {
        properties.setLogsDays(7);
        properties.setLogDir(logDir.toString());
        Path old = createLog("claude-mem.2026-10-01.0.log", NOW.minus(Duration.ofDays(10)).toEpochMilli());

        RetentionService.RetentionReport report = service.run(NOW, true, null);

        assertEquals(1, report.logFiles());
        assertTrue(Files.exists(old));
    }

    @Test
    @DisplayName("the scheduled job is a no-op while disabled")
    void scheduledJobDoesNothingWhenDisabled() {
        service.scheduledCleanup();

        verifyNoInteractions(jdbc);
    }

    private Path createLog(String name, long lastModifiedMillis) throws IOException {
        Path file = logDir.resolve(name);
        Files.writeString(file, "line\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(lastModifiedMillis));
        return file;
    }
}
