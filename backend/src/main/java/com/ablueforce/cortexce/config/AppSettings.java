package com.ablueforce.cortexce.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Application settings configuration.
 * <p>
 * Aligned with TS SettingsDefaults interface from SettingsDefaultsManager.ts.
 * Supports loading from file with environment variable overrides.
 * <p>
 * Configuration Priority:
 * 1. Environment variables (highest priority)
 * 2. Settings file (~/.claude-mem/settings.json)
 * 3. Default values (lowest priority)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {

    // LLM Configuration
    @JsonProperty("CLAUDE_MEM_MODEL")
    private String model = "claude-sonnet-4-5";

    @JsonProperty("CLAUDE_MEM_PROVIDER")
    private String provider = "claude";

    // Worker Configuration
    @JsonProperty("CLAUDE_MEM_WORKER_PORT")
    private String workerPort = "37777";

    @JsonProperty("CLAUDE_MEM_WORKER_HOST")
    private String workerHost = "127.0.0.1";

    @JsonProperty("CLAUDE_MEM_SKIP_TOOLS")
    private String skipTools = "ListMcpResourcesTool,SlashCommand,Skill,TodoWrite,AskUserQuestion";

    // Data Configuration
    @JsonProperty("CLAUDE_MEM_DATA_DIR")
    private String dataDir;

    @JsonProperty("CLAUDE_MEM_LOG_LEVEL")
    private String logLevel = "INFO";

    // Mode Configuration
    @JsonProperty("CLAUDE_MEM_MODE")
    private String mode = "code";

    // Context Configuration
    @JsonProperty("CLAUDE_MEM_CONTEXT_OBSERVATIONS")
    private String contextObservations = "50";

    @JsonProperty("CLAUDE_MEM_CONTEXT_FULL_COUNT")
    private String contextFullCount = "5";

    @JsonProperty("CLAUDE_MEM_CONTEXT_FULL_FIELD")
    private String contextFullField = "narrative";

    @JsonProperty("CLAUDE_MEM_CONTEXT_SESSION_COUNT")
    private String contextSessionCount = "10";

    // Token Economics Display
    @JsonProperty("CLAUDE_MEM_CONTEXT_SHOW_READ_TOKENS")
    private String contextShowReadTokens = "true";

    @JsonProperty("CLAUDE_MEM_CONTEXT_SHOW_WORK_TOKENS")
    private String contextShowWorkTokens = "true";

    @JsonProperty("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_AMOUNT")
    private String contextShowSavingsAmount = "true";

    @JsonProperty("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_PERCENT")
    private String contextShowSavingsPercent = "true";

    // Observation Filtering
    @JsonProperty("CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES")
    private String contextObservationTypes = "bugfix,feature,refactor,discovery,decision,change";

    @JsonProperty("CLAUDE_MEM_CONTEXT_OBSERVATION_CONCEPTS")
    private String contextObservationConcepts = "how-it-works,why-it-exists,what-changed,problem-solution,gotcha,pattern,trade-off";

    @JsonProperty("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS")
    private String contextMaxObservations = "50";

    // Feature Toggles
    @JsonProperty("CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY")
    private String contextShowLastSummary = "true";

    @JsonProperty("CLAUDE_MEM_CONTEXT_SHOW_LAST_MESSAGE")
    private String contextShowLastMessage = "false";

    @JsonProperty("CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED")
    private String folderClaudemdEnabled = "false";

    // Exclusion Settings
    @JsonProperty("CLAUDE_MEM_EXCLUDED_PROJECTS")
    private String excludedProjects = "";

    @JsonProperty("CLAUDE_MEM_FOLDER_MD_EXCLUDE")
    private String folderMdExclude = "[]";

    // Gemini Configuration
    @JsonProperty("CLAUDE_MEM_GEMINI_API_KEY")
    private String geminiApiKey = "";

    @JsonProperty("CLAUDE_MEM_GEMINI_MODEL")
    private String geminiModel = "gemini-2.5-flash-lite";

    // OpenRouter Configuration
    @JsonProperty("CLAUDE_MEM_OPENROUTER_API_KEY")
    private String openrouterApiKey = "";

    @JsonProperty("CLAUDE_MEM_OPENROUTER_MODEL")
    private String openrouterModel = "xiaomi/mimo-v2-flash:free";

    // Default constructor
    public AppSettings() {
        // Set dataDir default based on user home
        this.dataDir = System.getProperty("user.home") + "/.claude-mem";
    }

    // ===== Getters with Environment Variable Override =====

    public String getModel() {
        return getEnvOrDefault("CLAUDE_MEM_MODEL", model);
    }

    public String getProvider() {
        return getEnvOrDefault("CLAUDE_MEM_PROVIDER", provider);
    }

    public String getWorkerPort() {
        return getEnvOrDefault("CLAUDE_MEM_WORKER_PORT", workerPort);
    }

    public String getWorkerHost() {
        return getEnvOrDefault("CLAUDE_MEM_WORKER_HOST", workerHost);
    }

    public String getSkipTools() {
        return getEnvOrDefault("CLAUDE_MEM_SKIP_TOOLS", skipTools);
    }

    public String getDataDir() {
        return getEnvOrDefault("CLAUDE_MEM_DATA_DIR", dataDir);
    }

    public String getLogLevel() {
        return getEnvOrDefault("CLAUDE_MEM_LOG_LEVEL", logLevel);
    }

    public String getMode() {
        return getEnvOrDefault("CLAUDE_MEM_MODE", mode);
    }

    public String getContextObservations() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_OBSERVATIONS", contextObservations);
    }

    public String getContextFullCount() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_FULL_COUNT", contextFullCount);
    }

    public String getContextFullField() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_FULL_FIELD", contextFullField);
    }

    public String getContextSessionCount() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SESSION_COUNT", contextSessionCount);
    }

    public String getContextShowReadTokens() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SHOW_READ_TOKENS", contextShowReadTokens);
    }

    public String getContextShowWorkTokens() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SHOW_WORK_TOKENS", contextShowWorkTokens);
    }

    public String getContextShowSavingsAmount() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_AMOUNT", contextShowSavingsAmount);
    }

    public String getContextShowSavingsPercent() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_PERCENT", contextShowSavingsPercent);
    }

    public String getContextObservationTypes() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES", contextObservationTypes);
    }

    public String getContextObservationConcepts() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_OBSERVATION_CONCEPTS", contextObservationConcepts);
    }

    public String getContextMaxObservations() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS", contextMaxObservations);
    }

    public String getContextShowLastSummary() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY", contextShowLastSummary);
    }

    public String getContextShowLastMessage() {
        return getEnvOrDefault("CLAUDE_MEM_CONTEXT_SHOW_LAST_MESSAGE", contextShowLastMessage);
    }

    public String getFolderClaudemdEnabled() {
        return getEnvOrDefault("CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED", folderClaudemdEnabled);
    }

    public String getExcludedProjects() {
        return getEnvOrDefault("CLAUDE_MEM_EXCLUDED_PROJECTS", excludedProjects);
    }

    public String getFolderMdExclude() {
        return getEnvOrDefault("CLAUDE_MEM_FOLDER_MD_EXCLUDE", folderMdExclude);
    }

    public String getGeminiApiKey() {
        return getEnvOrDefault("CLAUDE_MEM_GEMINI_API_KEY", geminiApiKey);
    }

    public String getGeminiModel() {
        return getEnvOrDefault("CLAUDE_MEM_GEMINI_MODEL", geminiModel);
    }

    public String getOpenrouterApiKey() {
        return getEnvOrDefault("CLAUDE_MEM_OPENROUTER_API_KEY", openrouterApiKey);
    }

    public String getOpenrouterModel() {
        return getEnvOrDefault("CLAUDE_MEM_OPENROUTER_MODEL", openrouterModel);
    }

    // ===== Convenience methods for type conversion =====

    @JsonIgnore
    public int getContextObservationsInt() {
        return parseIntSafe(getContextObservations(), 50);
    }

    @JsonIgnore
    public int getContextFullCountInt() {
        return parseIntSafe(getContextFullCount(), 5);
    }

    @JsonIgnore
    public int getContextSessionCountInt() {
        return parseIntSafe(getContextSessionCount(), 10);
    }

    @JsonIgnore
    public boolean isContextShowReadTokens() {
        return "true".equalsIgnoreCase(getContextShowReadTokens());
    }

    @JsonIgnore
    public boolean isContextShowWorkTokens() {
        return "true".equalsIgnoreCase(getContextShowWorkTokens());
    }

    @JsonIgnore
    public boolean isContextShowSavingsAmount() {
        return "true".equalsIgnoreCase(getContextShowSavingsAmount());
    }

    @JsonIgnore
    public boolean isContextShowSavingsPercent() {
        return "true".equalsIgnoreCase(getContextShowSavingsPercent());
    }

    @JsonIgnore
    public boolean isContextShowLastSummary() {
        return "true".equalsIgnoreCase(getContextShowLastSummary());
    }

    @JsonIgnore
    public boolean isContextShowLastMessage() {
        return "true".equalsIgnoreCase(getContextShowLastMessage());
    }

    @JsonIgnore
    public boolean isFolderClaudemdEnabled() {
        return "true".equalsIgnoreCase(getFolderClaudemdEnabled());
    }

    @JsonIgnore
    public List<String> getContextObservationTypesList() {
        return parseCommaSeparated(getContextObservationTypes());
    }

    @JsonIgnore
    public List<String> getContextObservationConceptsList() {
        return parseCommaSeparated(getContextObservationConcepts());
    }

    @JsonIgnore
    public List<String> getExcludedProjectsList() {
        return parseCommaSeparated(getExcludedProjects());
    }

    // ===== Setters =====

    public void setModel(String model) { this.model = model; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setWorkerPort(String workerPort) { this.workerPort = workerPort; }
    public void setWorkerHost(String workerHost) { this.workerHost = workerHost; }
    public void setSkipTools(String skipTools) { this.skipTools = skipTools; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public void setMode(String mode) { this.mode = mode; }
    public void setContextObservations(String contextObservations) { this.contextObservations = contextObservations; }
    public void setContextFullCount(String contextFullCount) { this.contextFullCount = contextFullCount; }
    public void setContextFullField(String contextFullField) { this.contextFullField = contextFullField; }
    public void setContextSessionCount(String contextSessionCount) { this.contextSessionCount = contextSessionCount; }
    public void setContextShowReadTokens(String contextShowReadTokens) { this.contextShowReadTokens = contextShowReadTokens; }
    public void setContextShowWorkTokens(String contextShowWorkTokens) { this.contextShowWorkTokens = contextShowWorkTokens; }
    public void setContextShowSavingsAmount(String contextShowSavingsAmount) { this.contextShowSavingsAmount = contextShowSavingsAmount; }
    public void setContextShowSavingsPercent(String contextShowSavingsPercent) { this.contextShowSavingsPercent = contextShowSavingsPercent; }
    public void setContextObservationTypes(String contextObservationTypes) { this.contextObservationTypes = contextObservationTypes; }
    public void setContextObservationConcepts(String contextObservationConcepts) { this.contextObservationConcepts = contextObservationConcepts; }
    public void setContextMaxObservations(String contextMaxObservations) { this.contextMaxObservations = contextMaxObservations; }
    public void setContextShowLastSummary(String contextShowLastSummary) { this.contextShowLastSummary = contextShowLastSummary; }
    public void setContextShowLastMessage(String contextShowLastMessage) { this.contextShowLastMessage = contextShowLastMessage; }
    public void setFolderClaudemdEnabled(String folderClaudemdEnabled) { this.folderClaudemdEnabled = folderClaudemdEnabled; }
    public void setExcludedProjects(String excludedProjects) { this.excludedProjects = excludedProjects; }
    public void setFolderMdExclude(String folderMdExclude) { this.folderMdExclude = folderMdExclude; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }
    public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }
    public void setOpenrouterApiKey(String openrouterApiKey) { this.openrouterApiKey = openrouterApiKey; }
    public void setOpenrouterModel(String openrouterModel) { this.openrouterModel = openrouterModel; }

    // ===== Helper methods =====

    private String getEnvOrDefault(String envKey, String defaultValue) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    private int parseIntSafe(String value, int defaultVal) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","))
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /**
     * Convert to a Map for API responses.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("mode", getMode());
        map.put("provider", getProvider());
        map.put("model", getModel());
        map.put("logLevel", getLogLevel());
        map.put("full_observation_count", getContextFullCountInt());
        map.put("total_observation_count", getContextObservationsInt());
        map.put("session_count", getContextSessionCountInt());
        map.put("observation_types", getContextObservationTypesList());
        map.put("observation_concepts", getContextObservationConceptsList());
        map.put("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS", getContextMaxObservations());
        map.put("showReadTokens", isContextShowReadTokens());
        map.put("showWorkTokens", isContextShowWorkTokens());
        map.put("showSavingsAmount", isContextShowSavingsAmount());
        map.put("showSavingsPercent", isContextShowSavingsPercent());
        map.put("showLastSummary", isContextShowLastSummary());
        map.put("showLastMessage", isContextShowLastMessage());
        map.put("folderClaudemdEnabled", isFolderClaudemdEnabled());
        map.put("excludedProjects", getExcludedProjectsList());
        map.put("dataDir", getDataDir());
        return map;
    }
}
