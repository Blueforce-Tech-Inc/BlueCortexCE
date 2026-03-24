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

        ObservationsRequest request = ObservationsRequest.builder()
                .project(project)
                .limit(limit)
                .offset(offset)
                .build();

        Map<String, Object> result = client.listObservations(request);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /demo/observations/batch
     * Body: {"ids": ["id1", "id2", "id3"]}
     */
    @PostMapping(value = "/batch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getByIds(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids is required"));
        }

        Map<String, Object> result = client.getObservationsByIds(ids);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/observations/health
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "observations", "status", "ok"));
    }
}
