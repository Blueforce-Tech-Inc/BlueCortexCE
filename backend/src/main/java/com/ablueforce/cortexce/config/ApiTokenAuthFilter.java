package com.ablueforce.cortexce.config;

import com.ablueforce.cortexce.common.ErrorResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Optional bearer-token gate.
 *
 * <p><b>Default (no token configured):</b> every request passes. That keeps the documented
 * local-first usage — a single-user instance bound to {@code 127.0.0.1} — working without a
 * credential. <b>Before exposing the service to any other host, set
 * {@code cortexce.security.api-token}.</b>
 *
 * <p><b>Configured:</b> every mutating request under {@code /api/**} (anything except
 * GET/HEAD/OPTIONS) and the sensitive reads {@code GET /api/logs/**} require
 * {@code Authorization: Bearer <token>}. Everything else stays readable, because the Viewer
 * UI and the SSE stream cannot attach headers to an {@code EventSource} connection.
 *
 * <p>Comparison is constant-time ({@link MessageDigest#isEqual}) so a wrong token cannot be
 * recovered byte by byte from response timing. A missing or malformed credential and a
 * mismatched token all answer {@code 401} with {@code WWW-Authenticate: Bearer} and the
 * standard error envelope ({@code error.code = "unauthorized"}). {@code 403} is reserved
 * for a future authenticated-but-not-allowed distinction and is not emitted today.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public ApiTokenAuthFilter(@Value("${cortexce.security.api-token:}") String apiToken,
                              ObjectMapper objectMapper) {
        this.expectedToken = apiToken == null ? new byte[0]
            : apiToken.trim().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (expectedToken.length == 0) {
            return true;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("OPTIONS".equals(method) || "HEAD".equals(method)) {
            return true;
        }
        // Reads are open unless they expose operator-only data.
        if ("GET".equals(method)) {
            return !(path.equals("/api/logs") || path.startsWith("/api/logs/"));
        }
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && constantTimeMatch(header)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"cortexce\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponses.body(
            ErrorResponses.UNAUTHORIZED,
            header == null || header.isBlank()
                ? "Missing bearer token"
                : "Invalid bearer token"));
    }

    /**
     * Compare the supplied header against {@code "Bearer <token>"} without an early exit.
     * The scheme is matched case-insensitively per RFC 7235, the secret is not.
     */
    private boolean constantTimeMatch(String header) {
        int space = header.indexOf(' ');
        if (space <= 0) {
            return false;
        }
        String scheme = header.substring(0, space);
        if (!scheme.equalsIgnoreCase("Bearer")) {
            return false;
        }
        String supplied = header.substring(space + 1).trim();
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
        // MessageDigest.isEqual short-circuits on unequal lengths, so compare padded copies.
        int length = Math.max(suppliedBytes.length, expectedToken.length);
        byte[] left = new byte[length];
        byte[] right = new byte[length];
        System.arraycopy(suppliedBytes, 0, left, 0, suppliedBytes.length);
        System.arraycopy(expectedToken, 0, right, 0, expectedToken.length);
        return MessageDigest.isEqual(left, right) && suppliedBytes.length == expectedToken.length;
    }
}
