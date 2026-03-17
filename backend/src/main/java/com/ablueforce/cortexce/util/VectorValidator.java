package com.ablueforce.cortexce.util;

import com.ablueforce.cortexce.config.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P0: Vector string validation utility for PostgreSQL pgvector operations.
 * Validates vector format before executing queries to prevent SSRF/exploitation.
 */
public final class VectorValidator {

    private static final Logger log = LoggerFactory.getLogger(VectorValidator.class);

    // pgvector HNSW/IVFFlat indexes max at 2000 dimensions
    private static final int MAX_VECTOR_DIMENSION = 2000;
    // MAX_VECTOR_LENGTH moved to Constants.MAX_VECTOR_STRING_LENGTH

    private VectorValidator() {}

    /**
     * P0: Validate vector string format before passing to PostgreSQL.
     * Prevents potential SSRF or malformed query injection via vector parameters.
     * Uses safe parsing instead of regex to prevent ReDoS attacks.
     *
     * @param vectorStr The vector string to validate
     * @return true if valid, false if invalid
     */
    public static boolean isValidVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return false;
        }

        // P0: Check length to prevent ReDoS from huge inputs
        if (vectorStr.length() > Constants.MAX_VECTOR_STRING_LENGTH) {
            log.warn("Vector string too long: {} characters (max: {})",
                vectorStr.length(), Constants.MAX_VECTOR_STRING_LENGTH);
            return false;
        }

        // P0: Quick structural check
        if (vectorStr.charAt(0) != '[' || vectorStr.charAt(vectorStr.length() - 1) != ']') {
            log.warn("Vector must start with '[' and end with ']'");
            return false;
        }

        // P0: Parse and validate each element - avoids regex ReDoS
        String content = vectorStr.substring(1, vectorStr.length() - 1);
        int dimension = 0;

        int i = 0;
        while (i < content.length()) {
            // Skip whitespace before element
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= content.length()) break;

            // Parse one number
            if (!isValidNumberChar(content.charAt(i))) {
                log.warn("Invalid number start at position {}: {}", i, content.charAt(i));
                return false;
            }

            // Parse sign if present
            if (content.charAt(i) == '-' || content.charAt(i) == '+') {
                i++;
                if (i >= content.length()) {
                    log.warn("Unexpected end after sign at position {}", i);
                    return false;
                }
            }

            // Parse integer part and optional fraction
            boolean hasDigit = false;
            while (i < content.length() && Character.isDigit(content.charAt(i))) {
                hasDigit = true;
                i++;
            }
            // P0: Check bounds before accessing charAt - i could equal length here
            if (i < content.length() && content.charAt(i) == '.') {
                i++;
                while (i < content.length() && Character.isDigit(content.charAt(i))) {
                    hasDigit = true;
                    i++;
                }
            }
            if (!hasDigit) {
                log.warn("Missing digits in number at position {}", i);
                return false;
            }

            // Parse optional exponent
            if (i < content.length() && (content.charAt(i) == 'e' || content.charAt(i) == 'E')) {
                i++;
                if (i >= content.length()) {
                    log.warn("Unexpected end after exponent marker");
                    return false;
                }
                if (content.charAt(i) == '-' || content.charAt(i) == '+') {
                    i++;
                    if (i >= content.length()) {
                        log.warn("Unexpected end after exponent sign");
                        return false;
                    }
                }
                int expStart = i;
                while (i < content.length() && Character.isDigit(content.charAt(i))) {
                    i++;
                }
                if (i == expStart) {
                    log.warn("Missing exponent digits");
                    return false;
                }
            }

            dimension++;

            // Skip whitespace after element
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }

            // Expect comma or end
            if (i < content.length()) {
                if (content.charAt(i) != ',') {
                    log.warn("Expected comma or end, found: {}", content.charAt(i));
                    return false;
                }
                i++; // Skip comma
            }
        }

        // P0: Check dimension count
        if (dimension == 0) {
            log.warn("Vector has no elements");
            return false;
        }
        if (dimension > MAX_VECTOR_DIMENSION) {
            log.warn("Vector dimension too high: {} (max: {})", dimension, MAX_VECTOR_DIMENSION);
            return false;
        }

        return true;
    }

    /**
     * P3: Check if character is valid for number parsing (digit or decimal point).
     * Renamed from isValidNumberStart to accurately reflect behavior.
     */
    private static boolean isValidNumberChar(char c) {
        return Character.isDigit(c) || c == '.';
    }

    /**
     * P0: Count the number of dimensions in a vector string (simple version).
     */
    public static int countDimensions(String vectorStr) {
        if (vectorStr == null || vectorStr.length() < 2) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < vectorStr.length(); i++) {
            if (vectorStr.charAt(i) == ',') {
                count++;
            }
        }
        return count;
    }

    /**
     * P0: Sanitize vector string by removing potentially dangerous content.
     * Use this as a fallback when validation fails.
     * P0 FIX: Use iterative character-by-character sanitization instead of regex
     * to prevent ReDoS (Regular Expression Denial of Service) attacks.
     *
     * @param vectorStr The vector string to sanitize
     * @return Sanitized vector string or null if invalid
     */
    public static String sanitizeVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return null;
        }

        // P0: Use iterative sanitization to prevent ReDoS
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vectorStr.length(); i++) {
            char c = vectorStr.charAt(i);
            if (isValidVectorChar(c)) {
                sb.append(c);
            }
        }

        String sanitized = sb.toString();
        if (isValidVector(sanitized)) {
            return sanitized;
        }

        return null;
    }

    /**
     * P0: Check if character is valid for vector format.
     */
    private static boolean isValidVectorChar(char c) {
        return c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' ||
               c == ',' || c == '[' || c == ']' || Character.isDigit(c) || Character.isWhitespace(c);
    }
}
