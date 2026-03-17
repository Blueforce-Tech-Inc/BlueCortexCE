package com.ablueforce.cortexce.exception;

/**
 * P1: Custom exception for retryable errors.
 *
 * <p>Used to indicate errors that should be retried, such as:</p>
 * <ul>
 *   <li>Network timeouts</li>
 *   <li>LLM service temporarily unavailable</li>
 *   <li>Database connection issues</li>
 * </ul>
 */
public class RetryableException extends RuntimeException {

    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
