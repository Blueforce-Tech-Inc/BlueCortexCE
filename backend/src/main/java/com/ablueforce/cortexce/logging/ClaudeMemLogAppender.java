package com.ablueforce.cortexce.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Custom Logback Appender - generates TypeScript version compatible log format.
 *
 * <p>Log format:
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
 * <p>Features:
 * <ul>
 *   <li>LEVEL fixed to 5 characters, right-padded with spaces</li>
 *   <li>COMPONENT fixed to 6 characters, right-padded with spaces</li>
 *   <li>correlationId optional, from MDC</li>
 *   <li>Log files split by date</li>
 *   <li>File path: ~/.claude-mem/logs/claude-mem-{yyyy-MM-dd}.log</li>
 * </ul>
 *
 * <p>Configuration example (logback-spring.xml):
 * <pre>
 * &lt;appender name="CLAUDE_MEM_FILE" class="com.ablueforce.cortexce.logging.ClaudeMemLogAppender"&gt;
 *     &lt;logDir&gt;${user.home}/.claude-mem/logs&lt;/logDir&gt;
 * &lt;/appender&gt;
 * </pre>
 */
public class ClaudeMemLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String logDir;
    private volatile boolean initialized = false;

    /**
     * Set log directory (configured via logback-spring.xml)
     * @param logDir log directory path
     */
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    @Override
    public void start() {
        initializeLogDirectory();
        super.start();
        initialized = true;
    }

    private void initializeLogDirectory() {
        if (logDir == null || logDir.isEmpty()) {
            String home = System.getProperty("user.home");
            logDir = home + "/.claude-mem/logs";
        }

        File dir = new File(logDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("[ClaudeMemLogAppender] Created log directory: " + logDir);
            }
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!initialized || event == null) {
            return;
        }

        try {
            String logLine = formatLogLine(event);
            writeToFile(logLine);
        } catch (Exception e) {
            // Prevent logging system recursive calls
            System.err.println("[ClaudeMemLogAppender] Failed to write log: " + e.getMessage());
        }
    }

    /**
     * Format log line
     * Format: [timestamp] [LEVEL] [COMPONENT] [correlationId?] message
     */
    private String formatLogLine(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder(256);

        // 1. Timestamp: [2025-01-02 14:30:45.123]
        sb.append("[").append(formatTimestamp(event.getTimeStamp())).append("] ");

        // 2. Level: [INFO ] (5 characters, right-padded)
        sb.append("[").append(padRight(event.getLevel().toString(), 5)).append("] ");

        // 3. Component: [WORKER] (6 characters, right-padded, mapped from logger name)
        String component = mapLoggerToComponent(event.getLoggerName());
        sb.append("[").append(padRight(component, 6)).append("] ");

        // 4. Correlation ID: [obs-1-5] (from MDC, optional)
        Map<String, String> mdcMap = event.getMDCPropertyMap();
        String correlationId = mdcMap != null ? mdcMap.get("correlationId") : null;
        if (correlationId != null && !correlationId.isEmpty()) {
            sb.append("[").append(correlationId).append("] ");
        }

        // 5. Message (business code already includes special markers like → ✓ ✗)
        sb.append(event.getFormattedMessage());

        // 6. Exception (if any)
        if (event.getThrowableProxy() != null) {
            sb.append(" | ").append(event.getThrowableProxy().getMessage());
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * Map Logger name to Component
     */
    private String mapLoggerToComponent(String loggerName) {
        if (loggerName == null) {
            return "SYSTEM";
        }

        String lowerName = loggerName.toLowerCase();

        // Map by package name to Component
        if (lowerName.contains(".service.") || lowerName.contains("agentservice")) {
            return "SERVICE";
        }
        if (lowerName.contains(".controller.")) {
            return "HOOK  ";
        }
        if (lowerName.contains(".repository.") || lowerName.contains(".dao.")) {
            return "DB    ";
        }
        if (lowerName.contains("worker")) {
            return "WORKER";
        }
        if (lowerName.contains("sse") || lowerName.contains("stream")) {
            return "SSE   ";
        }
        if (lowerName.contains("ingest")) {
            return "INGEST";
        }
        if (lowerName.contains("search")) {
            return "SEARCH";
        }
        if (lowerName.contains("cursor")) {
            return "CURSOR";
        }
        if (lowerName.contains("mcp")) {
            return "MCP   ";
        }
        if (lowerName.contains("stale") || lowerName.contains("recovery")) {
            return "RECOV ";
        }

        // Default: use first 6 characters of class name
        String shortName = loggerName;
        int lastDot = loggerName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < loggerName.length() - 1) {
            shortName = loggerName.substring(lastDot + 1);
        }
        return padRight(shortName, 6);
    }

    /**
     * Format timestamp
     */
    private String formatTimestamp(long timeStamp) {
        Instant instant = Instant.ofEpochMilli(timeStamp);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateTime.format(TIMESTAMP_FORMATTER);
    }

    /**
     * Right-pad string to specified length
     */
    private String padRight(String s, int length) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= length) {
            return s.substring(0, length);
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(s);
        for (int i = s.length(); i < length; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Write log line to file
     */
    private synchronized void writeToFile(String logLine) throws IOException {
        String date = LocalDate.now().format(DATE_FORMATTER);
        Path logFile = Paths.get(logDir, "claude-mem-" + date + ".log");

        // Ensure parent directory exists
        Path parentDir = logFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.writeString(logFile, logLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
