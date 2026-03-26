package com.example.cortexmem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Directly calls backend /api/session/start; decouples from CortexMemClient version.
 * JitPack integration may lack startSession; this ensures demo runs in both modes.
 */
@Component
public class SessionStartClient {

    private final RestClient restClient;

    public SessionStartClient(@Value("${cortex.mem.base-url:http://localhost:37777}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> startSession(String sessionId, String projectPath) {
        try {
            return restClient.post()
                .uri("/api/session/start")
                .body(Map.of(
                    "session_id", sessionId,
                    "project_path", projectPath,
                    "cwd", projectPath
                ))
                .retrieve()
                .body(Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start session " + sessionId + ": " + e.getMessage(), e);
        }
    }
}
