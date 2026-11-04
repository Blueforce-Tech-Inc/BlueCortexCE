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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client token bucket for mutating {@code /api/**} requests.
 *
 * <p>This is the coarse, always-on layer that answers {@code 429 rate_limited} under a
 * flood (500 requests fired at once in the H2 measurement). {@code RateLimitService}
 * remains the narrower per-session limit on {@code POST /api/ingest/tool-use} (10 per
 * 60 s); the two do not replace each other.
 *
 * <p>Defaults: 200 tokens of burst and 100 tokens per second per client address. That is
 * far above the proxy's hook rate (a few events per second) and ordinary local editing, so
 * day-to-day development never sees a 429; a burst of 500 is capped. Every value is
 * configurable ({@code cortexce.http.rate-limit.*}), and {@code enabled=false} removes the
 * layer entirely.
 *
 * <p>The bucket is keyed by the socket address, not by {@code X-Forwarded-For}: the header
 * is caller-controlled and would let one client mint unlimited keys. Behind a reverse proxy
 * every caller shares the proxy address and therefore one bucket — acceptable for a
 * single-user service, and the reason the defaults are generous.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 12)
public class RequestRateLimitFilter extends OncePerRequestFilter {

    static final int DEFAULT_CAPACITY = 200;
    static final double DEFAULT_REFILL_PER_SECOND = 100.0;

    /** Above this many distinct addresses the map is emptied rather than grown further. */
    private static final int MAX_BUCKETS = 10_000;

    private final boolean enabled;
    private final int capacity;
    private final double refillPerSecond;
    private final ObjectMapper objectMapper;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RequestRateLimitFilter(
            @Value("${cortexce.http.rate-limit.enabled:true}") boolean enabled,
            @Value("${cortexce.http.rate-limit.capacity:" + DEFAULT_CAPACITY + "}") int capacity,
            @Value("${cortexce.http.rate-limit.refill-per-second:" + DEFAULT_REFILL_PER_SECOND + "}")
            double refillPerSecond,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.capacity = Math.max(1, capacity);
        this.refillPerSecond = Math.max(0.001, refillPerSecond);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (buckets.size() >= MAX_BUCKETS && !buckets.containsKey(request.getRemoteAddr())) {
            buckets.clear();
            buckets.put(request.getRemoteAddr(), new TokenBucket(capacity));
        }
        TokenBucket bucket = buckets.computeIfAbsent(
            String.valueOf(request.getRemoteAddr()), key -> new TokenBucket(capacity));

        if (!bucket.tryConsume(refillPerSecond, capacity)) {
            long retryAfter = Math.max(1, (long) Math.ceil(bucket.secondsUntilNextToken(refillPerSecond)));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), ErrorResponses.body(
                ErrorResponses.RATE_LIMITED, "Rate limit exceeded",
                Map.of("retry_after", String.valueOf(retryAfter),
                    "capacity", capacity,
                    "refill_per_second", refillPerSecond)));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Lazily refilled bucket; a token is one mutating request. */
    private static final class TokenBucket {

        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        TokenBucket(int capacity) {
            this.tokens = capacity;
        }

        synchronized boolean tryConsume(double refillPerSecond, int capacity) {
            refill(refillPerSecond, capacity);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized double secondsUntilNextToken(double refillPerSecond) {
            return refillPerSecond <= 0 ? 1 : Math.max(0, (1.0 - tokens) / refillPerSecond);
        }

        private void refill(double refillPerSecond, int capacity) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        }
    }
}
