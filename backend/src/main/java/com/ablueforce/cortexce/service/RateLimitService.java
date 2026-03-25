package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1: In-memory rate limiting service using sliding window algorithm.
 *
 * <p>Provides rate limiting for API endpoints to prevent abuse.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * if (!rateLimitService.tryAcquire("user:123", 10, 60)) {
 *     return ResponseEntity.status(429).body("Rate limit exceeded");
 * }
 * </pre>
 *
 * <p>Limits: 10 requests per 60 seconds per key.</p>
 *
 * <p>P3: Constants are configurable via application.yml or environment variables.</p>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // P3: Rate limit configuration - can be overridden via application.yml
    @Value("${claudemem.rate-limit.max-requests:10}")
    private int maxRequests;

    @Value("${claudemem.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${claudemem.rate-limit.cleanup-interval-seconds:300}")
    private long cleanupIntervalSeconds;

    private static final int MAX_WINDOWS = 10000;

    private final Map<String, SlidingWindow> windows = new ConcurrentHashMap<>();
    private volatile long lastCleanup = Instant.now().getEpochSecond();

    /**
     * P1: Sliding window counter for rate limiting.
     * Uses synchronized to ensure thread-safe window reset and increment.
     */
    private class SlidingWindow {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().getEpochSecond();

        boolean tryIncrement(long now) {
            synchronized (this) {
                // Check if window needs reset
                if (now - windowStart >= windowSeconds) {
                    count.set(0);
                    windowStart = now;
                }
                int current = count.incrementAndGet();
                return current <= maxRequests;
            }
        }

        int getCount() {
            long now = Instant.now().getEpochSecond();
            if (now - windowStart >= windowSeconds) {
                return 0;
            }
            return count.get();
        }

        long getRemainingSeconds() {
            long now = Instant.now().getEpochSecond();
            long elapsed = now - windowStart;
            return Math.max(0, windowSeconds - elapsed);
        }

        boolean isExpired(long now) {
            return now - windowStart >= windowSeconds;
        }
    }

    /**
     * P2: Clean up expired rate limit windows to prevent memory leak.
     * Also enforces MAX_WINDOWS limit to prevent unbounded memory growth.
     */
    private void cleanupExpiredWindows() {
        long now = Instant.now().getEpochSecond();
        if (now - lastCleanup < cleanupIntervalSeconds) {
            return;
        }

        lastCleanup = now;

        // Remove expired windows
        windows.entrySet().removeIf(entry -> {
            SlidingWindow window = entry.getValue();
            return window.isExpired(now);
        });

        // P2: Enforce maximum size cap to prevent memory exhaustion
        if (windows.size() > MAX_WINDOWS) {
            log.warn("Rate limit windows exceeded max size {}, cleaning up", MAX_WINDOWS);
            // Remove oldest entries (approximated by taking first N entries)
            windows.entrySet().stream()
                .limit(windows.size() - MAX_WINDOWS)
                .map(Map.Entry::getKey)
                .forEach(windows::remove);
        }

        if (windows.size() > 1000) {
            log.debug("Rate limit windows cleaned up, current size: {}", windows.size());
        }
    }

    /**
     * P1: Try to acquire a rate limit slot.
     *
     * @param key  Rate limit key (e.g., "user:123" or "ip:192.168.1.1")
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryAcquire(String key) {
        if (key == null || key.isEmpty()) {
            log.warn("Rate limit key is null or empty, using fallback key");
            // P1: Generate hash-based fallback key for privacy
            key = generateFallbackKey();
        }

        long now = Instant.now().getEpochSecond();
        SlidingWindow window = windows.computeIfAbsent(key, k -> new SlidingWindow());

        // P2: Periodic cleanup of expired windows
        cleanupExpiredWindows();

        boolean allowed = window.tryIncrement(now);

        if (!allowed) {
            log.debug("Rate limit exceeded for key: {}", key);
        }

        return allowed;
    }

    /**
     * P1: Get remaining requests for a key.
     *
     * @param key Rate limit key
     * @return Remaining requests, or 0 if rate limited
     */
    public int getRemainingRequests(String key) {
        if (key == null || key.isEmpty()) {
            return maxRequests;
        }
        SlidingWindow window = windows.get(key);
        if (window == null) {
            return maxRequests;
        }
        return Math.max(0, maxRequests - window.getCount());
    }

    /**
     * P1: Get seconds until rate limit resets.
     *
     * @param key Rate limit key
     * @return Seconds until reset, or 0 if not rate limited
     */
    public long getResetSeconds(String key) {
        if (key == null || key.isEmpty()) {
            return 0;
        }
        SlidingWindow window = windows.get(key);
        if (window == null) {
            return 0;
        }
        return window.getRemainingSeconds();
    }

    /**
     * P1: Clear rate limit data for a key.
     *
     * @param key Rate limit key
     */
    public void reset(String key) {
        if (key != null) {
            windows.remove(key);
        }
    }

    /**
     * P1: Clear all rate limit data.
     */
    public void resetAll() {
        windows.clear();
        log.info("All rate limit data cleared");
    }

    /**
     * P1: Validate IP address format to prevent header injection via X-Forwarded-For.
     * Supports both IPv4 and IPv6 addresses.
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > 45) {
            return false;
        }
        // IPv4 validation
        if (ip.matches("^[0-9.]+$")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                try {
                    for (String part : parts) {
                        int val = Integer.parseInt(part);
                        if (val < 0 || val > 255) return false;
                    }
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        }
        // IPv6 validation (compressed and full forms)
        if (ip.contains(":")) {
            // Use InetAddress for robust IPv6 parsing
            try {
                java.net.InetAddress addr = java.net.InetAddress.getByName(ip);
                return addr instanceof java.net.Inet6Address;
            } catch (java.net.UnknownHostException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * P1: Generate hash-based fallback key for privacy.
     * Uses timestamp bucket + random suffix to avoid exposing IP/thread info.
     */
    private String generateFallbackKey() {
        String remoteAddr = getRemoteAddr();
        long timestamp = Instant.now().getEpochSecond() / 60; // Per-minute granularity

        String identifier;
        if (remoteAddr != null && !remoteAddr.isEmpty() && !remoteAddr.equals("unknown")) {
            identifier = "ip:" + remoteAddr;
        } else {
            identifier = "thread:" + Thread.currentThread().getId();
        }

        // Hash for privacy (uses Java's hashCode which is consistent within JVM session)
        String hashBase = identifier + ":" + timestamp;
        int hash = hashBase.hashCode();
        String hashSuffix = Integer.toHexString(Math.abs(hash));

        return "fallback:" + hashSuffix + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * P1: Try to get remote address from request context.
     * This is a best-effort attempt as rate limiting may be called outside HTTP context.
     */
    private String getRemoteAddr() {
        try {
            // Try to get from InRequestContextHolder if available
            jakarta.servlet.http.HttpServletRequest request =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null
                    ? (jakarta.servlet.http.HttpServletRequest) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes().resolveReference("request")
                    : null;
            if (request != null) {
                String addr = request.getRemoteAddr();
                // P1: Validate X-Forwarded-For header to prevent injection
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isEmpty()) {
                    // Take the first IP in the chain (client IP)
                    String firstIp = forwarded.split(",")[0].trim();
                    // P1: Only use X-Forwarded-For if it's a valid IP
                    if (isValidIpAddress(firstIp)) {
                        addr = firstIp;
                    }
                }
                return addr;
            }
        } catch (Exception e) {
            // Not in HTTP context or other error - fall through to default
            log.debug("Could not get remote address: {}", e.getMessage());
        }
        return "unknown";
    }
}
