package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.common.LogMarkers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logs controller for the Viewer UI LogsModal.
 *
 * <p>Provides endpoints to retrieve and clear application logs in a format
 * compatible with the WebUI's LogsModal component.
 *
 * <p>Log format (TypeScript version compatible):
 * <pre>
 * [timestamp] [LEVEL] [COMPONENT] [correlationId?] message
 * </pre>
 *
 * <p>Examples:
 * <pre>
 * [2025-01-02 14:30:45.123] [INFO ] [WORKER] [obs-1-5] → Processing request
 * [2025-01-02 14:30:45.456] [DEBUG] [DB    ] [obs-1-5]     Query executed in 23ms
 * [2025-01-02 14:30:45.789] [ERROR] [HOOK  ]              ✗ Hook failed
 * </pre>
 *
 * <p>Log file location: ~/.claude-mem/logs/claude-mem-{yyyy-MM-dd}.log
 */
@RestController
@RequestMapping("/api/logs")
@Tag(name = "Logs", description = "Application log retrieval and management for the Viewer UI LogsModal")
public class LogsController {

    private static final Logger log = LoggerFactory.getLogger(LogsController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Log directory configuration
     * Default: ~/.claude-mem/logs
     */
    @Value("${claudemem.log.dir:${user.home}/.claude-mem/logs}")
    private String logDir;

    /**
     * GET /api/logs — retrieve application logs.
     *
     * <p>Supports:
     * <ul>
     *   <li>Automatically reads today's log file</li>
     *   <li>If last N lines are insufficient, falls back to previous day's file</li>
     *   <li>Supports level/component filtering (returns raw text)</li>
     * </ul>
     *
     * @param lines maximum number of lines to return (default: 1000)
     * @return log content response
     */
    @GetMapping
    @Operation(summary = "Retrieve application logs",
        description = "Returns application log entries from ~/.claude-mem/logs/. Reads today's log file first, falls back to yesterday if insufficient lines. Returns raw log text with metadata including file paths, total line count, and returned count. Lines parameter is clamped between 1 and 10000.")
    @ApiResponse(responseCode = "200", description = "Log content returned with metadata")
    public ResponseEntity<Map<String, Object>> getLogs(
            @Parameter(description = "Maximum number of log lines to return (1-10000, default 1000)", required = false, example = "1000")
            @RequestParam(defaultValue = "1000") int lines) {

        int validatedLines = Math.min(Math.max(1, lines), 10000);

        List<String> logLines = new ArrayList<>();
        int totalLines = 0;
        List<String> searchedFiles = new ArrayList<>();

        // Collect logs from today and yesterday files
        for (int dayOffset = 0; dayOffset <= 1 && logLines.size() < validatedLines; dayOffset++) {
            Path logFile = getLogFile(dayOffset);
            if (!Files.exists(logFile)) {
                continue;
            }

            searchedFiles.add(logFile.getFileName().toString());

            try {
                List<String> fileLines = Files.readAllLines(logFile);
                totalLines += fileLines.size();

                // Append to result (from file start, later files overwrite earlier lines)
                logLines.addAll(0, fileLines);
            } catch (IOException e) {
                log.warn("Failed to read log file {}: {}", logFile, e.getMessage());
            }
        }

        // Get last N lines
        int start = Math.max(0, logLines.size() - validatedLines);
        List<String> recentLines = logLines.subList(start, logLines.size());
        String result = String.join("\n", recentLines);

        log.info(LogMarkers.DATA_OUT + "Returning {} log lines from {} files",
                recentLines.size(), searchedFiles.size());

        return ResponseEntity.ok(Map.of(
                "logs", result,
                "path", logDir,
                "files", searchedFiles,
                "totalLines", totalLines,
                "returnedLines", recentLines.size(),
                "exists", !result.isEmpty()
        ));
    }

    /**
     * POST /api/logs/clear — clear today's log file.
     *
     * <p>Clears today's log file content.
     *
     * @return operation result
     */
    @PostMapping("/clear")
    @Operation(summary = "Clear today's log file",
        description = "Truncates today's application log file to empty content. Only affects today's log file. Returns the path of the cleared file.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Log file cleared successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to clear log file due to IO error")
    })
    public ResponseEntity<Map<String, String>> clearLogs() {
        Path todayLog = getLogFile(0);
        if (Files.exists(todayLog)) {
            try {
                Files.writeString(todayLog, "");
                log.info(LogMarkers.SUCCESS + "Log file cleared: {}", todayLog);
            } catch (IOException e) {
                log.error(LogMarkers.FAILURE + "Failed to clear log file: {}", todayLog, e);
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to clear log file"));
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Today's log file has been cleared",
                "path", todayLog.toString()
        ));
    }

    /**
     * Get log file path
     *
     * @param dayOffset 0=today, 1=yesterday, 2=day before...
     * @return log file path
     */
    private Path getLogFile(int dayOffset) {
        String date = LocalDate.now()
                .minusDays(dayOffset)
                .format(DATE_FORMATTER);
        return Paths.get(logDir, "claude-mem-" + date + ".log");
    }

    /**
     * Get log directory path
     *
     * @return log directory
     */
    public String getLogDir() {
        return logDir;
    }
}
