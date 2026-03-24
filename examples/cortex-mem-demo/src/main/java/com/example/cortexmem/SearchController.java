package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.SearchRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Demo controller for Search API.
 * Demonstrates semantic search with filtering.
 */
@RestController
@RequestMapping("/demo/search")
public class SearchController {

    private final CortexMemClient client;

    public SearchController(CortexMemClient client) {
        this.client = client;
    }

    /**
     * GET /demo/search?project=/test&query=hello&source=tool_result&limit=10&offset=0
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String project,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String observationType,
            @RequestParam(required = false) String concept,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset) {

        SearchRequest request = SearchRequest.builder()
                .project(project)
                .query(query)
                .type(observationType)
                .concept(concept)
                .source(source)
                .limit(limit)
                .offset(offset)
                .build();

        Map<String, Object> result = client.search(request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /demo/search/health
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("endpoint", "search", "status", "ok"));
    }
}
