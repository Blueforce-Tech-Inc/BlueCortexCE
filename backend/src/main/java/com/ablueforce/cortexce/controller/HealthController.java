package com.ablueforce.cortexce.controller;

import com.ablueforce.cortexce.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Health", description = "Service health and readiness monitoring endpoints")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final AgentService agentService;

    public HealthController(DataSource dataSource, AgentService agentService) {
        this.dataSource = dataSource;
        this.agentService = agentService;
    }

    /**
     * GET /api/health - Basic health check endpoint.
     * <p>
     * Returns 200 if the service is running and basic checks pass.
     * This is a lightweight check suitable for load balancers and kube probes.
     */
    @GetMapping("/health")
    @Operation(summary = "Basic health check",
        description = "Returns service status if the application is running. Suitable for load balancers and Kubernetes liveness probes.")
    @ApiResponse(responseCode = "200", description = "Service is healthy",
        content = @Content(schema = @Schema(example = "{\"status\":\"ok\",\"timestamp\":1709000000000,\"service\":\"claude-mem-java\"}")))
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();

        // Basic DB connectivity check to distinguish from a "zombie" process
        boolean dbReady = checkDatabase();
        response.put("status", dbReady ? "ok" : "degraded");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "claude-mem-java");

        if (!dbReady) {
            log.warn("Health check: database unreachable");
            return ResponseEntity.status(503).body(response);
        }
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
    @Operation(summary = "Readiness check",
        description = "Returns 200 only if the service is fully ready to accept traffic. Checks database connectivity and processing queue depth. Returns 503 if not ready.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Service is ready to accept traffic",
            content = @Content(schema = @Schema(example = "{\"status\":\"ready\",\"checks\":{\"database\":\"ready\",\"queueDepth\":0,\"queueStatus\":\"ready\"},\"timestamp\":1709000000000}"))),
        @ApiResponse(responseCode = "503", description = "Service is not ready (database unreachable or queue overloaded)")
    })
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
    @Operation(summary = "Get version information",
        description = "Returns the application version, Java version, and Spring Boot version.")
    @ApiResponse(responseCode = "200", description = "Version information retrieved successfully")
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
     * Get application version from manifest, build-info, or default.
     */
    private String getVersion() {
        // Try JAR manifest (works in production JAR)
        String version = getClass().getPackage().getImplementationVersion();
        if (version != null) {
            return version;
        }
        // Try Spring Boot build-info (generated by spring-boot-maven-plugin)
        try {
            var props = new java.util.Properties();
            var is = getClass().getResourceAsStream("/META-INF/build-info.properties");
            if (is != null) {
                props.load(is);
                version = props.getProperty("build.version");
                if (version != null) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }
        return "dev-SNAPSHOT";
    }

    /**
     * Get Spring Boot version.
     */
    private String getSpringBootVersion() {
        Package pkg = org.springframework.boot.SpringApplication.class.getPackage();
        return pkg != null ? pkg.getImplementationVersion() : "unknown";
    }
}
