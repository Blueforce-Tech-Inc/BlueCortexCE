package com.example.cortexmem;

import com.ablueforce.cortexce.client.CortexMemClient;
import com.ablueforce.cortexce.client.dto.SessionStartRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Session start wrapper — delegates to CortexMemClient.startSession().
 * <p>
 * Originally a separate RestClient implementation for JitPack compatibility,
 * now refactored to use the SDK's unified startSession API.
 */
@Component
public class SessionStartClient {

    private final CortexMemClient cortexMemClient;

    public SessionStartClient(CortexMemClient cortexMemClient) {
        this.cortexMemClient = cortexMemClient;
    }

    /**
     * Start a session via the SDK. Returns the backend response directly.
     */
    public Map<String, Object> startSession(String sessionId, String projectPath) {
        return cortexMemClient.startSession(
            SessionStartRequest.builder()
                .sessionId(sessionId)
                .projectPath(projectPath)
                .build()
        );
    }

    /**
     * Update session userId via the SDK.
     */
    public Map<String, Object> updateSessionUserId(String sessionId, String userId) {
        return cortexMemClient.updateSessionUserId(sessionId, userId);
    }
}
