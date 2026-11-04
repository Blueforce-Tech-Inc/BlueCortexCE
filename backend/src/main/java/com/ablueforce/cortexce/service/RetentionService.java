package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.RetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Age-based retention for prompts, observations, summaries and rolled log files.
 *
 * <p>Two properties make this safe to ship enabled: every window defaults to
 * {@code 0} (keep forever), and the job defaults to {@code dry-run}, where it
 * counts what it <em>would</em> delete and logs the numbers without writing. An
 * operator runs it once in dry-run, checks the counts, then sets
 * {@code cortexce.retention.dry-run=false}.
 *
 * <p>When {@code cortexce.retention.project} is set, the database cleanup adds a
 * {@code project_path = :project} predicate, so a scoped run cannot delete rows
 * from another project. Rows are selected by {@code created_at_epoch < cutoff};
 * a row exactly on the cutoff boundary is kept.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final RetentionProperties properties;

    public RetentionService(NamedParameterJdbcTemplate jdbc, RetentionProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /**
     * Scheduled entry point. Returns immediately unless retention is enabled.
     */
    @Scheduled(cron = "${cortexce.retention.cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        RetentionReport report = run(Instant.now(), properties.isDryRun(), properties.getProject());
        if (report.dryRun()) {
            log.info("Retention dry run: would delete prompts={} observations={} summaries={} logFiles={} (project={})",
                report.prompts(), report.observations(), report.summaries(), report.logFiles(),
                report.project() == null ? "all" : report.project());
        } else {
            log.info("Retention cleanup: deleted prompts={} observations={} summaries={} logFiles={} (project={})",
                report.prompts(), report.observations(), report.summaries(), report.logFiles(),
                report.project() == null ? "all" : report.project());
        }
    }

    /**
     * Run one cleanup pass.
     *
     * @param now     reference instant for the cutoffs (injected for tests)
     * @param dryRun  count only, never delete
     * @param project exact project scope, or null/blank for every project
     */
    public RetentionReport run(Instant now, boolean dryRun, String project) {
        String scope = project == null || project.isBlank() ? null : project;
        long prompts = cleanTable("mem_user_prompts", properties.getPromptsDays(), now, dryRun, scope);
        long observations = cleanTable("mem_observations", properties.getObservationsDays(), now, dryRun, scope);
        long summaries = cleanTable("mem_summaries", properties.getSummariesDays(), now, dryRun, scope);
        List<String> logFiles = cleanLogs(now, dryRun);
        return new RetentionReport(dryRun, scope, prompts, observations, summaries, logFiles.size());
    }

    private long cleanTable(String table, int days, Instant now, boolean dryRun, String project) {
        if (days <= 0) {
            return 0L;
        }
        long cutoff = now.minus(Duration.ofDays(days)).toEpochMilli();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("cutoff", cutoff);
        String predicate = "created_at_epoch < :cutoff";
        if (project != null) {
            predicate += " AND project_path = :project";
            params.addValue("project", project);
        }
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE " + predicate, params, Long.class);
        long rows = count == null ? 0L : count;
        if (!dryRun && rows > 0) {
            long deleted = jdbc.update("DELETE FROM " + table + " WHERE " + predicate, params);
            if (deleted != rows) {
                log.warn("Retention deleted {} rows from {} but counted {}; concurrent writes are expected",
                    deleted, table, rows);
            }
            return deleted;
        }
        return rows;
    }

    private List<String> cleanLogs(Instant now, boolean dryRun) {
        int days = properties.getLogsDays();
        if (days <= 0) {
            return List.of();
        }
        Path dir = Path.of(properties.resolvedLogDir());
        if (!Files.isDirectory(dir)) {
            log.warn("Retention log directory does not exist: {}", dir);
            return List.of();
        }
        long cutoff = now.minus(Duration.ofDays(days)).toEpochMilli();
        String prefix = properties.getLogFilePrefix();
        List<String> expired = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(RetentionService::isLogFile)
                .filter(path -> lastModified(path) < cutoff)
                .forEach(path -> {
                    expired.add(path.getFileName().toString());
                    if (!dryRun) {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Retention could not delete log file {}: {}", path, e.getMessage());
                        }
                    }
                });
        } catch (IOException e) {
            log.warn("Retention could not list log directory {}: {}", dir, e.getMessage());
            return List.of();
        }
        return expired;
    }

    private static boolean isLogFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".log") || name.endsWith(".log.gz") || name.contains(".log.");
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE; // treat unreadable files as fresh rather than deleting them
        }
    }

    /**
     * Outcome of one pass. Counts are what was deleted (real run) or what would
     * have been deleted (dry run).
     */
    public record RetentionReport(
        boolean dryRun,
        String project,
        long prompts,
        long observations,
        long summaries,
        long logFiles
    ) {
        public long databaseRows() {
            return prompts + observations + summaries;
        }
    }
}
