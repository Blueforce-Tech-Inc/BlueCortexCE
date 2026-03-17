package com.ablueforce.cortexce.config;

/**
 * Application-wide constants.
 * Centralizes magic numbers and configuration values for maintainability.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // ==========================================================================
    // Pagination
    // ==========================================================================

    /** Default page size for paginated API responses */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Maximum allowed page size for paginated API responses */
    public static final int MAX_PAGE_SIZE = 100;

    // ==========================================================================
    // Text Length Limits
    // ==========================================================================

    /**
     * Maximum length for user prompt text storage.
     * User prompts can be quite long, so we allow up to 100KB.
     */
    public static final int MAX_USER_PROMPT_LENGTH = 100000;

    /**
     * Maximum length for tool input/output when building LLM prompts.
     * LLM prompts need to be more concise to fit within token limits.
     */
    public static final int MAX_TOOL_CONTENT_LENGTH = 4000;

    /**
     * Maximum length for vector string representation.
     * Used for validation before storing in database.
     */
    public static final int MAX_VECTOR_STRING_LENGTH = 65536;

    // ==========================================================================
    // Rate Limiting
    // ==========================================================================

    /** Default rate limit: requests per window */
    public static final int RATE_LIMIT_REQUESTS = 10;

    /** Default rate limit: window duration in seconds */
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    // ==========================================================================
    // SSE
    // ==========================================================================

    /** Maximum number of concurrent SSE connections */
    public static final int MAX_SSE_CONNECTIONS = 100;

    // ==========================================================================
    // Timeouts
    // ==========================================================================

    /** Default SSE timeout in milliseconds (30 minutes) */
    public static final long SSE_TIMEOUT_MS = 1800000;
}
