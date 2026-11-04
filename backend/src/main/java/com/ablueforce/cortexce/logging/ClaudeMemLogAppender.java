package com.ablueforce.cortexce.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.util.FileSize;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
 *   <li>Size rotation: the active (newest) file stays at the date-named path so
 *       {@code GET /api/logs} keeps reading the latest lines; once it reaches
 *       {@code maxFileSize} it is renamed to a timestamped part and a fresh active
 *       file starts.</li>
 *   <li>Total-size cap: after each rotation the oldest parts are deleted until the
 *       {@code claude-mem-*} files in the directory fit {@code totalSizeCap}.</li>
 * </ul>
 *
 * <p>Before this rotation existed the appender wrote one unbounded file per day: on the
 * reference machine 105 daily files reached 1.5 GB, with single days at 105 MB. The
 * defaults are 100 MB per part and a 1 GB directory cap, both configurable through
 * {@code cortexce.logging.*}.
 *
 * <p>Configuration example (logback-spring.xml):
 * <pre>
 * &lt;appender name="CLAUDE_MEM_FILE" class="com.ablueforce.cortexce.logging.ClaudeMemLogAppender"&gt;
 *     &lt;logDir&gt;${user.home}/.claude-mem/logs&lt;/logDir&gt;
 *     &lt;maxFileSize&gt;100MB&lt;/maxFileSize&gt;
 *     &lt;totalSizeCap&gt;1GB&lt;/totalSizeCap&gt;
 * &lt;/appender&gt;
 * </pre>
 */
public class ClaudeMemLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PART_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String PREFIX = "claude-mem-";

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    private static final long DEFAULT_TOTAL_SIZE_CAP_BYTES = 1024L * 1024 * 1024;
    private static final long STAT_INTERVAL_LINES = 100;

    private String logDir;
    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    private long totalSizeCapBytes = DEFAULT_TOTAL_SIZE_CAP_BYTES;
    private volatile boolean initialized = false;

    /** Cached size of the active file; -1 means "stat before the next append". */
    private long cachedSize = -1;
    private String cachedDate;
    private long linesSinceStat;

    /**
     * Set log directory (configured via logback-spring.xml)
     * @param logDir log directory path
     */
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    /** Maximum size of one log part; the value is a Logback {@link FileSize} ("100MB"). */
    public void setMaxFileSize(FileSize maxFileSize) {
        this.maxFileSizeBytes = maxFileSize == null ? DEFAULT_MAX_FILE_SIZE_BYTES : maxFileSize.getSize();
    }

    /** Cap for every {@code claude-mem-*} file in the directory; "1GB" by default. */
    public void setTotalSizeCap(FileSize totalSizeCap) {
        this.totalSizeCapBytes = totalSizeCap == null ? DEFAULT_TOTAL_SIZE_CAP_BYTES : totalSizeCap.getSize();
    }

    @Override
    public void start() {
        initializeLogDirectory();
        super.start();
        initialized = true;
        cleanupOldParts(null);
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
     * Write log line to file, rotating at {@link #maxFileSizeBytes}.
     *
     * <p>The active file keeps the date-named path so {@code GET /api/logs} always reads
     * the newest lines. A full file is renamed to
     * {@code claude-mem-<date>.<yyyyMMdd-HHmmss-SSS>.log} and writing continues in a new
     * active file. The size is cached and re-stat'ed every 100 lines: a stat per line would
     * cost more than the write, while the interval bounds drift if someone truncates the
     * file from outside (the {@code /api/logs/clear} endpoint does exactly that).
     */
    private synchronized void writeToFile(String logLine) throws IOException {
        String date = LocalDate.now().format(DATE_FORMATTER);
        Path logFile = Paths.get(logDir, "claude-mem-" + date + ".log");

        // Ensure parent directory exists
        Path parentDir = logFile.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        byte[] bytes = logLine.getBytes(StandardCharsets.UTF_8);
        if (cachedDate == null || !cachedDate.equals(date)) {
            cachedDate = date;
            cachedSize = -1;
            linesSinceStat = 0;
        }
        if (cachedSize < 0 || ++linesSinceStat >= STAT_INTERVAL_LINES) {
            cachedSize = Files.exists(logFile) ? Files.size(logFile) : 0;
            linesSinceStat = 0;
        }
        if (cachedSize > 0 && cachedSize + bytes.length > maxFileSizeBytes) {
            rotate(logFile, date);
            cachedSize = 0;
            cleanupOldParts(logFile);
        }

        Files.write(logFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        cachedSize += bytes.length;
    }

    /**
     * Renames the full active file to a timestamped part. A single line larger than the
     * limit is still written: refusing it would silently drop a log record.
     */
    private void rotate(Path activeFile, String date) throws IOException {
        if (Files.exists(activeFile) && Files.size(activeFile) > 0) {
            String suffix = LocalDateTime.now().format(PART_FORMATTER);
            Path part = activeFile.resolveSibling(PREFIX + date + "." + suffix + ".log");
            int attempt = 1;
            while (Files.exists(part)) {
                part = activeFile.resolveSibling(
                    PREFIX + date + "." + suffix + "-" + attempt + ".log");
                attempt++;
            }
            Files.move(activeFile, part);
        }
    }

    /**
     * Deletes the oldest {@code claude-mem-*} files until the directory fits
     * {@link #totalSizeCapBytes}. The active file is never deleted.
     */
    private synchronized void cleanupOldParts(Path activeFile) {
        Path dir = Paths.get(logDir);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> candidates = new ArrayList<>();
            long total = 0;
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith(PREFIX) || !name.endsWith(".log") || !Files.isRegularFile(path)) {
                    continue;
                }
                total += Files.size(path);
                if (activeFile == null || !path.equals(activeFile)) {
                    candidates.add(path);
                }
            }
            if (total <= totalSizeCapBytes || candidates.isEmpty()) {
                return;
            }
            candidates.sort(Comparator.comparingLong(ClaudeMemLogAppender::lastModifiedMillis));
            int removed = 0;
            for (Path candidate : candidates) {
                if (total <= totalSizeCapBytes) {
                    break;
                }
                long size = Files.size(candidate);
                if (Files.deleteIfExists(candidate)) {
                    total -= size;
                    removed++;
                }
            }
            if (removed > 0) {
                System.out.println("[ClaudeMemLogAppender] Removed " + removed
                    + " old log part(s) to stay under " + totalSizeCapBytes + " bytes");
            }
        } catch (IOException e) {
            if (initialized) {
                System.err.println("[ClaudeMemLogAppender] Log cleanup failed: " + e.getMessage());
            }
        }
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }
}
