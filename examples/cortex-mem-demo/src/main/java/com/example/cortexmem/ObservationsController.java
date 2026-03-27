package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ObservationsRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.List;

/**
 * Demo controller for Observations List API.
 * Demonstrates paginated observation listing.
 */
@RestController
@RequestMapping("/demo/observations")
public class ObservationsController {

    private final CortexMemClient client;

    public ObservationsController(CortexMemClient client) {
        this.client = client;
    }

    /**
     * GET /demo/observations?project=/test&limit=10&offset=0
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> listObservations(
            @RequestParam(required = false) String project,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {

        if (project == null || project.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "project is required"));
        }
        if (limit < 0 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be between 0 and 100"));
        }
        if (offset < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "offset must be non-negative"));
        }

        try {
            ObservationsRequest request = ObservationsRequest.builder()
                    .project(project)
                    .limit(limit)
                    .offset(offset)
                    .build();

            Map<String, Object> result = client.listObservations(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "List observations failed: " + e.getMessage()));
        }
    }

    /**
     * POST /demo/observations/batch
     * Body: {"ids": ["id1", "id2", "id3"]}
     */
    @PostMapping(value = "/batch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getByIds(@RequestBody Map<String, List<String>> body) {
        if (body == null || !body.containsKey("ids")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request body must contain 'ids' field"));
        }
        List<String> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids must not be empty"));
        }
        if (ids.size() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "batch size exceeds maximum of 100"));
        }

        try {
            Map<String, Object> result = client.getObservationsByIds(ids);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Batch observations failed: " + e.getMessage()));
        }
    }

    /**
     * GET /demo/observations/health
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "observations", "status", "ok"));
    }
}
