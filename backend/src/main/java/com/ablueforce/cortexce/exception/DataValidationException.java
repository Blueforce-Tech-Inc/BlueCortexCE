package com.ablueforce.cortexce.exception;

/**
 * P1: Custom exception for data validation errors.
 *
 * <p>Used to indicate errors that should NOT be retried, such as:</p>
 * <ul>
 *   <li>Invalid XML format from LLM response</li>
 *   <li>Missing required fields</li>
 *   <li>Malformed input data</li>
 * </ul>
 */
public class DataValidationException extends RuntimeException {

    public DataValidationException(String message) {
        super(message);
    }

    public DataValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
