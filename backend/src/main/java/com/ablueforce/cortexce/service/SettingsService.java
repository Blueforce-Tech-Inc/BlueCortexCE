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

    private AppSettings settings;
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

            // Atomic rename
            Files.move(tempPath, settingsPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);

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
            settings.setMode(String.valueOf(updates.get("mode")));
        }
        if (updates.containsKey("CLAUDE_MEM_MODE")) {
            settings.setMode(String.valueOf(updates.get("CLAUDE_MEM_MODE")));
        }
        if (updates.containsKey("model")) {
            settings.setModel(String.valueOf(updates.get("model")));
        }
        if (updates.containsKey("CLAUDE_MEM_MODEL")) {
            settings.setModel(String.valueOf(updates.get("CLAUDE_MEM_MODEL")));
        }
        if (updates.containsKey("provider")) {
            settings.setProvider(String.valueOf(updates.get("provider")));
        }
        if (updates.containsKey("CLAUDE_MEM_PROVIDER")) {
            settings.setProvider(String.valueOf(updates.get("CLAUDE_MEM_PROVIDER")));
        }
        if (updates.containsKey("logLevel")) {
            settings.setLogLevel(String.valueOf(updates.get("logLevel")));
        }
        if (updates.containsKey("CLAUDE_MEM_LOG_LEVEL")) {
            settings.setLogLevel(String.valueOf(updates.get("CLAUDE_MEM_LOG_LEVEL")));
        }
        if (updates.containsKey("full_observation_count")) {
            settings.setContextFullCount(String.valueOf(updates.get("full_observation_count")));
        }
        if (updates.containsKey("total_observation_count")) {
            settings.setContextObservations(String.valueOf(updates.get("total_observation_count")));
        }
        if (updates.containsKey("session_count")) {
            settings.setContextSessionCount(String.valueOf(updates.get("session_count")));
        }
        if (updates.containsKey("observationTypes")) {
            Object types = updates.get("observationTypes");
            if (types instanceof java.util.List<?> list) {
                settings.setContextObservationTypes(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                settings.setContextObservationTypes(String.valueOf(types));
            }
        }
        if (updates.containsKey("observationConcepts")) {
            Object concepts = updates.get("observationConcepts");
            if (concepts instanceof java.util.List<?> list) {
                settings.setContextObservationConcepts(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                settings.setContextObservationConcepts(String.valueOf(concepts));
            }
        }
        if (updates.containsKey("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS")) {
            settings.setContextMaxObservations(String.valueOf(updates.get("CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS")));
        }
        if (updates.containsKey("showReadTokens")) {
            settings.setContextShowReadTokens(String.valueOf(updates.get("showReadTokens")));
        }
        if (updates.containsKey("showWorkTokens")) {
            settings.setContextShowWorkTokens(String.valueOf(updates.get("showWorkTokens")));
        }
        if (updates.containsKey("showSavingsAmount")) {
            settings.setContextShowSavingsAmount(String.valueOf(updates.get("showSavingsAmount")));
        }
        if (updates.containsKey("showSavingsPercent")) {
            settings.setContextShowSavingsPercent(String.valueOf(updates.get("showSavingsPercent")));
        }
        if (updates.containsKey("showLastSummary")) {
            settings.setContextShowLastSummary(String.valueOf(updates.get("showLastSummary")));
        }
        if (updates.containsKey("showLastMessage")) {
            settings.setContextShowLastMessage(String.valueOf(updates.get("showLastMessage")));
        }
        if (updates.containsKey("folderClaudemdEnabled")) {
            settings.setFolderClaudemdEnabled(String.valueOf(updates.get("folderClaudemdEnabled")));
        }
        if (updates.containsKey("excludedProjects")) {
            Object excluded = updates.get("excludedProjects");
            if (excluded instanceof java.util.List<?> list) {
                settings.setExcludedProjects(String.join(",", list.stream().map(String::valueOf).toList()));
            } else {
                settings.setExcludedProjects(String.valueOf(excluded));
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
