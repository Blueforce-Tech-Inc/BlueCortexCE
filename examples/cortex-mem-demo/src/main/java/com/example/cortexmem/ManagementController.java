package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Demo controller for Management APIs (P1).
 * Demonstrates version, stats, modes, and settings.
 */
@RestController
@RequestMapping("/demo/manage")
public class ManagementController {

    private final CortexMemClient client;

    public ManagementController(CortexMemClient client) {
        this.client = client;
    }

    /**
     * GET /demo/manage/version
     */
    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getVersion() {
        Map<String, Object> result = client.getVersion();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/manage/stats?project=/test
     */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getStats(@RequestParam(required = false) String project) {
        Map<String, Object> result = client.getStats(project);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/manage/modes
     */
    @GetMapping(value = "/modes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getModes() {
        Map<String, Object> result = client.getModes();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/manage/settings
     */
    @GetMapping(value = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSettings() {
        Map<String, Object> result = client.getSettings();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/manage/health
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "manage", "status", "ok"));
    }
}
