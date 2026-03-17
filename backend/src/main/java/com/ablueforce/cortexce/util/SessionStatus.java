package com.ablueforce.cortexce.util;

/**
 * P1: Constants for session and message status values.
 * Centralizes status strings to prevent typos and ensure consistency.
 */
public final class SessionStatus {

    // Session status values
    public static final String ACTIVE = "active";
    public static final String COMPLETED = "completed";
    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String FAILED = "failed";
    public static final String SKIPPED = "skipped";

    private SessionStatus() {
        // Prevent instantiation
    }
}
