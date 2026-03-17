package com.ablueforce.cortexce.common;

import org.slf4j.Logger;

/**
 * Logging shortcut interface - provides concise API with consistent format as TypeScript version.
 *
 * <p>Usage: Service class implements this interface, then use shortcut methods:
 * <pre>
 * &#64;Service
 * public class MyService implements LogHelper {
 *     private final Logger logger = LoggerFactory.getLogger(MyService.class);
 *
 *     &#64;Override
 *     public Logger getLogger() {
 *         return logger;
 *     }
 *
 *     public void processData(String input) {
 *         logDataIn("Received data: " + input);
 *         // ... processing ...
 *         logSuccess("Processing complete");
 *         logTiming("processData", 42);
 *     }
 * }
 * </pre>
 *
 * <p>Output format:
 * <pre>
 * [2025-01-02 14:30:45.123] [INFO ] [WORKER] [obs-1-5] → Received data: hello
 * [2025-01-02 14:30:45.456] [INFO ] [WORKER] [obs-1-5] ✓ Processing complete
 * [2025-01-02 14:30:45.789] [INFO ] [WORKER] [obs-1-5] ⏱ processData took 42ms
 * </pre>
 */
public interface LogHelper {

    /**
     * Get Logger instance
     * @return SLF4J Logger
     */
    Logger getLogger();

    /**
     * Shortcut method for Logger
     */
    default Logger logger() {
        return getLogger();
    }

    // ========== Data Flow ==========

    /**
     * Log data input
     * @param message log message
     */
    default void logDataIn(String message) {
        logger().info(LogMarkers.DATA_IN + message);
    }

    /**
     * Log data input (with parameters)
     * @param format message format
     * @param args parameters
     */
    default void logDataIn(String format, Object... args) {
        logger().info(LogMarkers.DATA_IN + format, args);
    }

    /**
     * Log data output
     * @param message log message
     */
    default void logDataOut(String message) {
        logger().info(LogMarkers.DATA_OUT + message);
    }

    /**
     * Log data output (with parameters)
     * @param format message format
     * @param args parameters
     */
    default void logDataOut(String format, Object... args) {
        logger().info(LogMarkers.DATA_OUT + format, args);
    }

    // ========== Success/Failure ==========

    /**
     * Log success
     * @param message log message
     */
    default void logSuccess(String message) {
        logger().info(LogMarkers.SUCCESS + message);
    }

    /**
     * Log success (with parameters)
     * @param format message format
     * @param args parameters
     */
    default void logSuccess(String format, Object... args) {
        logger().info(LogMarkers.SUCCESS + format, args);
    }

    /**
     * Log failure (ERROR level)
     * @param message log message
     */
    default void logFailure(String message) {
        logger().error(LogMarkers.FAILURE + message);
    }

    /**
     * Log failure (ERROR level, with parameters)
     * @param format message format
     * @param args parameters
     */
    default void logFailure(String format, Object... args) {
        logger().error(LogMarkers.FAILURE + format, args);
    }

    /**
     * Log failure (ERROR level, with exception)
     * @param message log message
     * @param t exception
     */
    default void logFailure(String message, Throwable t) {
        logger().error(LogMarkers.FAILURE + message, t);
    }

    // ========== Timing ==========

    /**
     * Log timing
     * @param operation operation name
     * @param durationMs duration in milliseconds
     */
    default void logTiming(String operation, long durationMs) {
        logger().info(LogMarkers.TIMING + "{} took {}ms", operation, durationMs);
    }

    /**
     * Log timing (with custom format)
     * @param message full message
     */
    default void logTiming(String message) {
        logger().info(LogMarkers.TIMING + message);
    }

    // ========== Fallback ==========

    /**
     * Log fallback (WARN level)
     * @param message log message
     */
    default void logHappyPath(String message) {
        logger().warn(LogMarkers.HAPPY_PATH + message);
    }

    /**
     * Log fallback (WARN level, with parameters)
     * @param format message format
     * @param args parameters
     */
    default void logHappyPath(String format, Object... args) {
        logger().warn(LogMarkers.HAPPY_PATH + format, args);
    }

    // ========== Alignment (Java version specific) ==========

    /**
     * Log session alignment
     * @param message log message
     */
    default void logAlignment(String message) {
        logger().info(LogMarkers.ALIGNMENT + message);
    }

    /**
     * Log session alignment (with parameters)
     * @param format message format
     * @param args parameters
     */
    default void logAlignment(String format, Object... args) {
        logger().info(LogMarkers.ALIGNMENT + format, args);
    }
}
