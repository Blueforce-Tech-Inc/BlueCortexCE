package com.ablueforce.cortexce.client.config;

import java.time.Duration;

/**
 * Configuration properties for Cortex CE memory system connection.
 *
 * Bind to {@code cortex.mem.*} in application.yml.
 */
public class CortexMemProperties {

    private String baseUrl = "http://localhost:37777";
    private String projectPath;
    /** API key for authentication (sent as Bearer token). Null/blank = no auth. */
    private String apiKey;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int defaultExperienceCount = 4;
    /** Master switch for tool capture (@Tool observations). */
    private boolean captureEnabled = true;
    /** Fine-grained: enable/disable user prompt auto-capture in CortexMemoryAdvisor. */
    private boolean captureUserPromptEnabled = true;
    private boolean retrievalEnabled = true;
    /** Create CortexMemoryTools bean for on-demand memory retrieval. Default false (opt-in). */
    private boolean memoryToolsEnabled = false;
    /** Enable CortexSessionContextBridgeAdvisor to auto begin/end context when CONVERSATION_ID is set. Default true. */
    private boolean contextBridgeEnabled = true;
    private Retry retry = new Retry();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    public int getDefaultExperienceCount() { return defaultExperienceCount; }
    public void setDefaultExperienceCount(int count) { this.defaultExperienceCount = count; }

    public boolean isCaptureEnabled() { return captureEnabled; }
    public void setCaptureEnabled(boolean captureEnabled) { this.captureEnabled = captureEnabled; }

    public boolean isCaptureUserPromptEnabled() { return captureUserPromptEnabled; }
    public void setCaptureUserPromptEnabled(boolean captureUserPromptEnabled) { this.captureUserPromptEnabled = captureUserPromptEnabled; }

    public boolean isRetrievalEnabled() { return retrievalEnabled; }
    public void setRetrievalEnabled(boolean retrievalEnabled) { this.retrievalEnabled = retrievalEnabled; }

    public boolean isMemoryToolsEnabled() { return memoryToolsEnabled; }
    public void setMemoryToolsEnabled(boolean memoryToolsEnabled) { this.memoryToolsEnabled = memoryToolsEnabled; }

    public boolean isContextBridgeEnabled() { return contextBridgeEnabled; }
    public void setContextBridgeEnabled(boolean contextBridgeEnabled) { this.contextBridgeEnabled = contextBridgeEnabled; }

    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration backoff = Duration.ofMillis(500);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public Duration getBackoff() { return backoff; }
        public void setBackoff(Duration backoff) { this.backoff = backoff; }
    }
}
