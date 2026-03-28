package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.ObservationsRequest;
import com.ablueforce.cortexce.client.dto.ObservationUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ObservationsController.class);

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
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "limit must be between 1 and 100"));
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
            log.error("List observations failed for project={}", project, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "List observations failed: " + e.getMessage()));
        }
    }

    /**
     * POST /demo/observations/batch
     * Body: {"ids": ["id1", "id2", "id3"]}
     */
    @PostMapping(value = "/batch", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getByIds(@RequestBody(required = false) Map<String, List<String>> body) {
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
            log.error("Batch observations failed (ids count={})", ids.size(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Batch observations failed: " + e.getMessage()));
        }
    }

    /**
     * PATCH /demo/observations/{id}
     * Body: {"title":"...", "source":"...", "extractedData":{...}}
     *
     * Demonstrates V14 observation update with partial fields.
     * Only non-null fields are updated (PATCH semantics).
     */
    @PatchMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateObservation(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "observation id is required"));
        }
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "request body must contain at least one field to update"));
        }
        try {
            ObservationUpdate.Builder builder = ObservationUpdate.builder();
            if (body.containsKey("title")) {
                if (!(body.get("title") instanceof String)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "title must be a string"));
                }
                builder.title((String) body.get("title"));
            }
            if (body.containsKey("subtitle")) {
                if (!(body.get("subtitle") instanceof String)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "subtitle must be a string"));
                }
                builder.subtitle((String) body.get("subtitle"));
            }
            if (body.containsKey("content")) {
                if (!(body.get("content") instanceof String)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "content must be a string"));
                }
                builder.content((String) body.get("content"));
            }
            if (body.containsKey("narrative")) {
                if (!(body.get("narrative") instanceof String)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "narrative must be a string"));
                }
                builder.narrative((String) body.get("narrative"));
            }
            if (body.containsKey("facts")) {
                Object factsObj = body.get("facts");
                if (!(factsObj instanceof List<?> factsList)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "facts must be a list of strings"));
                }
                // Validate all items are strings (reject nulls — String.valueOf(null) returns "null")
                List<String> validatedFacts = new java.util.ArrayList<>();
                for (Object item : factsList) {
                    if (!(item instanceof String)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "facts must contain only strings"));
                    }
                    validatedFacts.add((String) item);
                }
                builder.facts(validatedFacts);
            }
            if (body.containsKey("concepts")) {
                Object conceptsObj = body.get("concepts");
                if (!(conceptsObj instanceof List<?> conceptsList)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "concepts must be a list of strings"));
                }
                // Validate all items are strings (reject nulls — String.valueOf(null) returns "null")
                List<String> validatedConcepts = new java.util.ArrayList<>();
                for (Object item : conceptsList) {
                    if (!(item instanceof String)) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "concepts must contain only strings"));
                    }
                    validatedConcepts.add((String) item);
                }
                builder.concepts(validatedConcepts);
            }
            if (body.containsKey("source")) {
                if (!(body.get("source") instanceof String)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "source must be a string"));
                }
                builder.source((String) body.get("source"));
            }
            if (body.containsKey("extractedData")) {
                Object extractedDataObj = body.get("extractedData");
                if (!(extractedDataObj instanceof Map<?, ?>)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "extractedData must be a map"));
                }
                builder.extractedData((Map<String, Object>) extractedDataObj);
            }
            client.updateObservation(id, builder.build());
            return ResponseEntity.ok(Map.of("status", "updated", "id", id));
        } catch (Exception e) {
            log.error("Update observation failed for id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Update observation failed: " + e.getMessage()));
        }
    }

    /**
     * DELETE /demo/observations/{id}
     *
     * Demonstrates V14 observation deletion.
     */
    @DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> deleteObservation(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "observation id is required"));
        }
        try {
            client.deleteObservation(id);
            return ResponseEntity.ok(Map.of("status", "deleted", "id", id));
        } catch (Exception e) {
            log.error("Delete observation failed for id={}", id, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Delete observation failed: " + e.getMessage()));
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
