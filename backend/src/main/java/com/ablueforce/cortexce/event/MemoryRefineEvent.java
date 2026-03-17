package com.ablueforce.cortexce.event;

import java.time.OffsetDateTime;

/**
 * Event fired when a session ends and memory refinement should be triggered.
 * 
 * This follows the architecture: Spring Event → @Async EventListener → scheduled task fallback.
 */
public class MemoryRefineEvent {

    private final String projectPath;
    private final String sessionId;
    private final OffsetDateTime timestamp;
    private final RefineType refineType;

    public enum RefineType {
        SESSION_END,    // Triggered by session end (real-time)
        SCHEDULED,     // Triggered by scheduled task (fallback)
        MANUAL          // Manually triggered via API
    }

    public MemoryRefineEvent(String projectPath, String sessionId, RefineType refineType) {
        this.projectPath = projectPath;
        this.sessionId = sessionId;
        this.timestamp = OffsetDateTime.now();
        this.refineType = refineType;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getSessionId() {
        return sessionId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public RefineType getRefineType() {
        return refineType;
    }

    @Override
    public String toString() {
        return "MemoryRefineEvent{" +
                "projectPath='" + projectPath + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", refineType=" + refineType +
                ", timestamp=" + timestamp +
                '}';
    }
}
