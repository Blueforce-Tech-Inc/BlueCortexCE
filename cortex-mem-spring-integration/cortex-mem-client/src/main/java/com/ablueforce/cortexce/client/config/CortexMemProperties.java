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
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int defaultExperienceCount = 4;
    private boolean captureEnabled = true;
    private boolean retrievalEnabled = true;
    private Retry retry = new Retry();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    public int getDefaultExperienceCount() { return defaultExperienceCount; }
    public void setDefaultExperienceCount(int count) { this.defaultExperienceCount = count; }

    public boolean isCaptureEnabled() { return captureEnabled; }
    public void setCaptureEnabled(boolean captureEnabled) { this.captureEnabled = captureEnabled; }

    public boolean isRetrievalEnabled() { return retrievalEnabled; }
    public void setRetrievalEnabled(boolean retrievalEnabled) { this.retrievalEnabled = retrievalEnabled; }

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
