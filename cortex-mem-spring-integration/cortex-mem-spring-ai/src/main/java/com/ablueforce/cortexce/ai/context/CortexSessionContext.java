package com.ablueforce.cortexce.ai.context;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-local session context for tracking the current AI session.
 * <p>
 * Provides session ID, project path, and prompt counter
 * that can be referenced by the AOP aspect and capture service.
 */
public final class CortexSessionContext {

    private static final ThreadLocal<SessionInfo> CURRENT = new ThreadLocal<>();

    private CortexSessionContext() {}

    public static void begin(String projectPath) {
        begin(UUID.randomUUID().toString(), projectPath);
    }

    public static void begin(String sessionId, String projectPath) {
        CURRENT.set(new SessionInfo(sessionId, projectPath));
    }

    public static void end() {
        CURRENT.remove();
    }

    public static String getSessionId() {
        var info = CURRENT.get();
        return info != null ? info.sessionId : "unknown-session";
    }

    public static String getProjectPath() {
        var info = CURRENT.get();
        return info != null ? info.projectPath : "";
    }

    public static int incrementAndGetPromptNumber() {
        var info = CURRENT.get();
        return info != null ? info.promptCounter.incrementAndGet() : 0;
    }

    public static int getPromptNumber() {
        var info = CURRENT.get();
        return info != null ? info.promptCounter.get() : 0;
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    private static class SessionInfo {
        final String sessionId;
        final String projectPath;
        final AtomicInteger promptCounter = new AtomicInteger(0);

        SessionInfo(String sessionId, String projectPath) {
            this.sessionId = sessionId;
            this.projectPath = projectPath;
        }
    }
}
