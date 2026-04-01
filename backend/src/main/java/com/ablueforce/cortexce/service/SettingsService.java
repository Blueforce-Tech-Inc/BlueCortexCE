package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.AppSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

/**
 * Settings management service.
 * <p>
 * Handles loading, saving, and managing application settings.
 * Settings are stored in ~/.claude-mem/settings.json.
 * <p>
 * Configuration Priority:
 * 1. Environment variables (highest priority)
 * 2. Settings file (~/.claude-mem/settings.json)
 * 3. Default values (lowest priority)
 * <p>
 * Aligned with TS SettingsDefaultsManager.ts
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private volatile AppSettings settings;
    private Path settingsPath;

    @PostConstruct
    public void init() {
        this.settingsPath = resolveSettingsPath();
        this.settings = loadSettings();
        log.info("SettingsService initialized. Settings path: {}", settingsPath);
    }

    /**
     * Resolve the settings file path.
     * Priority: CLAUDE_MEM_DATA_DIR env > user.home/.claude-mem
     */
    private Path resolveSettingsPath() {
        String dataDir = System.getenv("CLAUDE_MEM_DATA_DIR");
        if (dataDir != null && !dataDir.isBlank()) {
            return Paths.get(dataDir, "settings.json");
        }
        return Paths.get(System.getProperty("user.home"), ".claude-mem", "settings.json");
    }

    /**
     * Load settings from file with fallback to defaults.
     * Creates the file with defaults if it doesn't exist.
     */
    private AppSettings loadSettings() {
        try {
            // Ensure directory exists
            Path parentDir = settingsPath.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("Created settings directory: {}", parentDir);
            }

            // Load from file or create with defaults
            if (Files.exists(settingsPath)) {
                String content = Files.readString(settingsPath);

                // Handle migration from nested schema { env: {...} } to flat schema
                if (content.contains("\"env\"")) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nested = objectMapper.readValue(content, Map.class);
                        if (nested.containsKey("env")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> envMap = (Map<String, Object>) nested.get("env");
                            content = objectMapper.writeValueAsString(envMap);
                            // Auto-migrate the file
                            Files.writeString(settingsPath, content);
                            log.info("Migrated settings file from nested to flat schema");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to migrate nested settings schema: {}", e.getMessage());
                    }
                }

                AppSettings loaded = objectMapper.readValue(content, AppSettings.class);
                log.info("Loaded settings from file: {}", settingsPath);
                return loaded;
            } else {
                // Create file with defaults
                AppSettings defaults = new AppSettings();
                saveSettings(defaults);
                log.info("Created settings file with defaults: {}", settingsPath);
                return defaults;
            }
        } catch (IOException e) {
            log.warn("Failed to load settings from {}, using defaults: {}", settingsPath, e.getMessage());
            return new AppSettings();
        }
    }

    /**
     * Save settings to file.
     * Uses atomic write (temp file + rename) for safety.
     */
    public void saveSettings(AppSettings settings) {
        try {
            // Ensure directory exists
            Path parentDir = settingsPath.getParent();
            if (!Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Write to temp file first (atomic write)
            Path tempPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp");
            String content = objectMapper.writeValueAsString(settings);
            Files.writeString(tempPath, content);

            // Atomic rename (fallback to non-atomic if cross-filesystem)
            try {
                Files.move(tempPath, settingsPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                log.warn("Atomic move not supported (cross-filesystem?), falling back to regular rename");
                Files.move(tempPath, settingsPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            this.settings = settings;
            log.info("Saved settings to: {}", settingsPath);
        } catch (IOException e) {
            log.error("Failed to save settings to {}: {}", settingsPath, e.getMessage());
            throw new RuntimeException("Failed to save settings", e);
        }
    }

    /**
     * Get current settings.
     * Note: Getters automatically apply environment variable overrides.
     */
    public AppSettings getSettings() {
        return settings;
    }

    /**
     * Update settings partially.
     * Only updates the provided fields, keeps others unchanged.
     */
    public AppSettings updateSettings(Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return settings;
        }

        // Apply updates to settings object
        if (updates.containsKey("mode")) {
            settings.setMode(Objects.toString(updates.get("mode"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_MODE")) {
            settings.setMode(Objects.toString(updates.get("CLAUDE_MEM_MODE"), ""));
        }
        if (updates.containsKey("model")) {
            settings.setModel(Objects.toString(updates.get("model"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_MODEL")) {
            settings.setModel(Objects.toString(updates.get("CLAUDE_MEM_MODEL"), ""));
        }
        if (updates.containsKey("provider")) {
            settings.setProvider(Objects.toString(updates.get("provider"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_PROVIDER")) {
            settings.setProvider(Objects.toString(updates.get("CLAUDE_MEM_PROVIDER"), ""));
        }
        if (updates.containsKey("logLevel")) {
            settings.setLogLevel(Objects.toString(updates.get("logLevel"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_LOG_LEVEL")) {
            settings.setLogLevel(Objects.toString(updates.get("CLAUDE_MEM_LOG_LEVEL"), ""));
        }
        if (updates.containsKey("full_observation_count")) {
            settings.setContextFullCount(Objects.toString(updates.get("full_observation_count"), ""));
        }
        if (updates.containsKey("total_observation_count")) {
            settings.setContextObservations(Objects.toString(updates.get("total_observation_count"), ""));
        }
        if (updates.containsKey("session_count")) {
            settings.setContextSessionCount(Objects.toString(updates.get("session_count"), ""));
        }
        if (updates.containsKey("observation_types")) {
            Object types = updates.get("observation_types");
            if (types instanceof java.util.List<?> list) {
                settings.setContextObservationTypes(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                settings.setContextObservationTypes(Objects.toString(types, ""));
            }
        }
        if (updates.containsKey("observation_concepts")) {
            Object concepts = updates.get("observation_concepts");
            if (concepts instanceof java.util.List<?> list) {
                settings.setContextObservationConcepts(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                settings.setContextObservationConcepts(Objects.toString(concepts, ""));
            }
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS")) {
            settings.setContextMaxObservations(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS"), ""));
        }
        // Accept CLAUDE_MEM_* prefixed names for fields that WebUI sends
        if (updates.containsKey("CLAUDE_MEM_WORKER_PORT")) {
            settings.setWorkerPort(Objects.toString(updates.get("CLAUDE_MEM_WORKER_PORT"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_WORKER_HOST")) {
            settings.setWorkerHost(Objects.toString(updates.get("CLAUDE_MEM_WORKER_HOST"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_OBSERVATIONS")) {
            settings.setContextObservations(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_OBSERVATIONS"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_FULL_COUNT")) {
            settings.setContextFullCount(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_FULL_COUNT"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_FULL_FIELD")) {
            settings.setContextFullField(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_FULL_FIELD"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SESSION_COUNT")) {
            settings.setContextSessionCount(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SESSION_COUNT"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SHOW_READ_TOKENS")) {
            settings.setContextShowReadTokens(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SHOW_READ_TOKENS"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SHOW_WORK_TOKENS")) {
            settings.setContextShowWorkTokens(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SHOW_WORK_TOKENS"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_AMOUNT")) {
            settings.setContextShowSavingsAmount(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_AMOUNT"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_PERCENT")) {
            settings.setContextShowSavingsPercent(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SHOW_SAVINGS_PERCENT"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY")) {
            settings.setContextShowLastSummary(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SHOW_LAST_SUMMARY"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_SHOW_LAST_MESSAGE")) {
            settings.setContextShowLastMessage(Objects.toString(updates.get("CLAUDE_MEM_CONTEXT_SHOW_LAST_MESSAGE"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED")) {
            settings.setFolderClaudemdEnabled(Objects.toString(updates.get("CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_GEMINI_API_KEY")) {
            settings.setGeminiApiKey(Objects.toString(updates.get("CLAUDE_MEM_GEMINI_API_KEY"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_GEMINI_MODEL")) {
            settings.setGeminiModel(Objects.toString(updates.get("CLAUDE_MEM_GEMINI_MODEL"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_OPENROUTER_API_KEY")) {
            settings.setOpenrouterApiKey(Objects.toString(updates.get("CLAUDE_MEM_OPENROUTER_API_KEY"), ""));
        }
        if (updates.containsKey("CLAUDE_MEM_OPENROUTER_MODEL")) {
            settings.setOpenrouterModel(Objects.toString(updates.get("CLAUDE_MEM_OPENROUTER_MODEL"), ""));
        }
        if (updates.containsKey("showReadTokens")) {
            settings.setContextShowReadTokens(Objects.toString(updates.get("showReadTokens"), ""));
        }
        if (updates.containsKey("showWorkTokens")) {
            settings.setContextShowWorkTokens(Objects.toString(updates.get("showWorkTokens"), ""));
        }
        if (updates.containsKey("showSavingsAmount")) {
            settings.setContextShowSavingsAmount(Objects.toString(updates.get("showSavingsAmount"), ""));
        }
        if (updates.containsKey("showSavingsPercent")) {
            settings.setContextShowSavingsPercent(Objects.toString(updates.get("showSavingsPercent"), ""));
        }
        if (updates.containsKey("showLastSummary")) {
            settings.setContextShowLastSummary(Objects.toString(updates.get("showLastSummary"), ""));
        }
        if (updates.containsKey("showLastMessage")) {
            settings.setContextShowLastMessage(Objects.toString(updates.get("showLastMessage"), ""));
        }
        if (updates.containsKey("folderClaudemdEnabled")) {
            settings.setFolderClaudemdEnabled(Objects.toString(updates.get("folderClaudemdEnabled"), ""));
        }
        if (updates.containsKey("excludedProjects")) {
            Object excluded = updates.get("excludedProjects");
            if (excluded instanceof java.util.List<?> list) {
                settings.setExcludedProjects(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                settings.setExcludedProjects(Objects.toString(excluded, ""));
            }
        }

        // Save to file
        saveSettings(settings);

        return settings;
    }

    /**
     * Get the settings file path.
     */
    public Path getSettingsPath() {
        return settingsPath;
    }

    /**
     * Reload settings from file.
     */
    public void reloadSettings() {
        this.settings = loadSettings();
        log.info("Reloaded settings from file");
    }
}
