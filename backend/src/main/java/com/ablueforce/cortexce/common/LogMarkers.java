package com.ablueforce.cortexce.common;

/**
 * Log marker constants for structured logging.
 * Maintains format consistency with TypeScript version.
 *
 * <p>Usage examples:
 * <pre>
 * logger.info(LogMarkers.DATA_IN + "Received observation");
 * logger.info(LogMarkers.SUCCESS + "Processing complete");
 * logger.error(LogMarkers.FAILURE + "Request failed");
 * </pre>
 *
 * <p>Output format:
 * <pre>
 * [2025-01-02 14:30:45.123] [INFO ] [WORKER] [obs-1-5] → Received observation
 * [2025-01-02 14:30:45.456] [INFO ] [WORKER] [obs-1-5] ✓ Processing complete
 * [2025-01-02 14:30:45.789] [ERROR] [HOOK  ]              ✗ Request failed
 * </pre>
 */
public final class LogMarkers {

    // ========== Data Flow Markers ==========

    /** Data input → Processing input data */
    public static final String DATA_IN = "→ ";

    /** Data output ← Output data */
    public static final String DATA_OUT = "← ";

    // ========== Status Markers ==========

    /** Success ✓ Operation succeeded */
    public static final String SUCCESS = "✓ ";

    /** Failure ✗ Operation failed */
    public static final String FAILURE = "✗ ";

    /** Timing ⏱ Timing information */
    public static final String TIMING = "⏱ ";

    // ========== Special Markers ==========

    /** Fallback [HAPPY-PATH] Fallback used */
    public static final String HAPPY_PATH = "[HAPPY-PATH] ";

    /** Alignment [ALIGNMENT] Session alignment (Java version specific) */
    public static final String ALIGNMENT = "[ALIGNMENT] ";

    // Private constructor to prevent instantiation
    private LogMarkers() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }
}
