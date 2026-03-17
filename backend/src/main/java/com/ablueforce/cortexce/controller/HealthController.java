package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Health and readiness endpoints for service monitoring.
 * <p>
 * Provides API-compatible endpoints matching the TS version:
 * - GET /api/health - Basic health check
 * - GET /api/readiness - Readiness check for traffic
 * <p>
 * Note: Spring Boot Actuator also provides /actuator/health with more details.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AgentService agentService;

    /**
     * GET /api/health - Basic health check endpoint.
     * <p>
     * Returns 200 if the service is running and basic checks pass.
     * This is a lightweight check suitable for load balancers and kube probes.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "claude-mem-java");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/readiness - Readiness check endpoint.
     * <p>
     * Returns 200 only if the service is fully ready to accept traffic.
     * Checks:
     * - Database connectivity
     * - No critical processing backlogs
     * <p>
     * Returns 503 if not ready.
     */
    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> checks = new HashMap<>();
        boolean allReady = true;

        // Check database connectivity
        boolean dbReady = checkDatabase();
        checks.put("database", dbReady ? "ready" : "not_ready");
        if (!dbReady) allReady = false;

        // Check processing queue (not critical, but informative)
        long queueDepth = agentService.getQueueDepth();
        checks.put("queueDepth", queueDepth);
        checks.put("queueStatus", queueDepth < 100 ? "ready" : "degraded");

        Map<String, Object> response = new HashMap<>();
        response.put("status", allReady ? "ready" : "not_ready");
        response.put("checks", checks);
        response.put("timestamp", System.currentTimeMillis());

        if (allReady) {
            return ResponseEntity.ok(response);
        } else {
            log.warn("Readiness check failed: {}", checks);
            return ResponseEntity.status(503).body(response);
        }
    }

    /**
     * GET /api/version - Version information endpoint.
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> version() {
        Map<String, Object> response = new HashMap<>();
        response.put("version", getVersion());
        response.put("service", "claude-mem-java");
        response.put("java", System.getProperty("java.version"));
        response.put("springBoot", getSpringBootVersion());
        return ResponseEntity.ok(response);
    }

    /**
     * Check database connectivity.
     */
    private boolean checkDatabase() {
        try (var conn = dataSource.getConnection()) {
            return conn.isValid(5); // 5 second timeout
        } catch (SQLException e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get application version from manifest or default.
     */
    private String getVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "0.1.0-SNAPSHOT";
    }

    /**
     * Get Spring Boot version.
     */
    private String getSpringBootVersion() {
        Package pkg = org.springframework.boot.SpringApplication.class.getPackage();
        return pkg != null ? pkg.getImplementationVersion() : "unknown";
    }
}
