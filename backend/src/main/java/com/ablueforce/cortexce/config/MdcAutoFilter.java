package com.ablueforce.cortexce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Automatic MDC management Filter.
 *
 * <p>Automatically sets MDC (Mapped Diagnostic Context) for each HTTP request,
 * so that all logs within the request scope automatically include correlationId.
 *
 * <p>Values in MDC are read by ClaudeMemLogAppender to generate TypeScript version compatible log format:
 * <pre>
 * [2025-01-02 14:30:45.123] [INFO ] [WORKER] [correlation-id] → Processing request
 * </pre>
 *
 * <p>Prefers to read correlationId from request header, if not present, generates automatically.
 */
@Component
@Order(1)
public class MdcAutoFilter extends OncePerRequestFilter {

    /**
     * MDC key for correlation ID
     */
    public static final String CORRELATION_ID = "correlationId";

    /**
     * MDC key for session ID (optional)
     */
    public static final String SESSION_ID = "sessionId";

    /**
     * Request header name for correlation ID
     */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Request header name for session ID
     */
    private static final String SESSION_ID_HEADER = "X-Session-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        try {
            // Set correlationId
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                // Auto-generate 8-character short ID
                correlationId = UUID.randomUUID().toString().substring(0, 8);
            }
            MDC.put(CORRELATION_ID, correlationId);

            // Set sessionId (optional)
            String sessionId = request.getHeader(SESSION_ID_HEADER);
            if (sessionId != null && !sessionId.isEmpty()) {
                MDC.put(SESSION_ID, sessionId);
            }

            // Add correlationId to response header for tracking
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            chain.doFilter(request, response);
        } finally {
            // Clear MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Manually set correlationId (for non-HTTP request scenarios)
     * @param correlationId correlation ID
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }

    /**
     * Manually set sessionId
     * @param sessionId session ID
     */
    public static void setSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            MDC.put(SESSION_ID, sessionId);
        }
    }

    /**
     * Get current correlationId
     * @return correlationId or null
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID);
    }

    /**
     * Get current sessionId
     * @return sessionId or null
     */
    public static String getSessionId() {
        return MDC.get(SESSION_ID);
    }

    /**
     * Clear all MDC values (for manual MDC management scenarios)
     */
    public static void clearMdc() {
        MDC.clear();
    }
}
