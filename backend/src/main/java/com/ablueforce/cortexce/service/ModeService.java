package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.config.ModeConfig.Mode;
import com.ablueforce.cortexce.config.ModeConfig.ObservationType;
import com.ablueforce.cortexce.config.ModeConfig.ObservationConcept;
import com.ablueforce.cortexce.config.ModeConfig.ModePrompts;
import com.ablueforce.cortexce.common.LogHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class ModeService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(ModeService.class);
    private static final String DEFAULT_MODE = "code";

    @Override
    public Logger getLogger() {
        return log;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${claudemem.mode:code}")
    private String configuredMode;

    @Value("${claudemem.modes-dir:}")
    private String configuredModesDir;

    private Path modesDir;
    private Mode activeMode;
    private final Map<String, Mode> modeCache = new HashMap<>();

    @PostConstruct
    public void init() {
        this.modesDir = resolveModesDir();
        logDataIn("ModeService initialized with modes directory: {}", modesDir);

        String modeId = getConfiguredModeId();
        try {
            this.activeMode = loadMode(modeId);
            logSuccess("Loaded active mode: {} ({})", activeMode.name(), modeId);
        } catch (Exception e) {
            logHappyPath("Failed to load mode '{}', using embedded default: {}", modeId, e.getMessage());
            this.activeMode = createDefaultMode();
        }
    }

    /**
     * Create a default mode configuration when no mode files are available.
     * This is used as the ultimate fallback when the classpath and filesystem both fail.
     */
    private Mode createDefaultMode() {
        List<ObservationType> types = List.of(
            new ObservationType("bugfix", "Bug Fix", "Something was broken, now fixed", "🔴", "🛠️"),
            new ObservationType("feature", "Feature", "New capability or functionality added", "🟣", "🛠️"),
            new ObservationType("refactor", "Refactor", "Code restructured, behavior unchanged", "🔄", "🛠️"),
            new ObservationType("change", "Change", "Generic modification (docs, config, misc)", "✅", "🛠️"),
            new ObservationType("discovery", "Discovery", "Learning about existing system", "🔵", "🔍"),
            new ObservationType("decision", "Decision", "Architectural/design choice with rationale", "⚖️", "⚖️")
        );

        List<ObservationConcept> concepts = List.of(
            new ObservationConcept("how-it-works", "How It Works", "Understanding mechanisms"),
            new ObservationConcept("why-it-exists", "Why It Exists", "Purpose or rationale"),
            new ObservationConcept("what-changed", "What Changed", "Modifications made"),
            new ObservationConcept("problem-solution", "Problem-Solution", "Issues and their fixes"),
            new ObservationConcept("gotcha", "Gotcha", "Traps or edge cases"),
            new ObservationConcept("pattern", "Pattern", "Reusable approach"),
            new ObservationConcept("trade-off", "Trade-Off", "Pros/cons of a decision")
        );

        // Minimal prompts for default mode
        ModePrompts prompts = new ModePrompts(
            "You are a Claude-Mem, a specialized observer tool for creating searchable memory FOR FUTURE SESSIONS.",
            null, // language_instruction
            "SPATIAL AWARENESS: Tool executions include the working directory to help you understand context.",
            "Your job is to monitor a Claude Code session and create observations as work is being done.",
            "Focus on deliverables and capabilities. Use verbs like: implemented, fixed, deployed, configured.",
            "Skip routine operations like empty status checks and simple file listings.",
            "**type**: MUST be EXACTLY one of: bugfix, feature, refactor, change, discovery, decision",
            "**concepts**: 2-5 categories from: how-it-works, why-it-exists, what-changed, problem-solution, gotcha, pattern, trade-off",
            "**facts**: Concise, self-contained statements. **files**: All files touched.",
            "OUTPUT FORMAT\n-------------\nOutput observations using this XML structure:",
            "", // format_examples
            "IMPORTANT! DO NOT do any work other than generating observations.",
            "[**title**: Short title]",
            "[**subtitle**: One sentence explanation]",
            "[Concise fact]",
            "[**narrative**: Full context]",
            "[concept]",
            "[path/to/file]",
            "[Request summary]",
            "[What was explored]",
            "[What was learned]",
            "[What was completed]",
            "[Next steps]",
            "[Notes]",
            "MEMORY PROCESSING START\n=======================",
            "MEMORY PROCESSING CONTINUED\n===========================",
            "PROGRESS SUMMARY CHECKPOINT\n===========================",
            "Hello memory agent, you are continuing to observe the session.",
            "IMPORTANT: Continue generating observations from tool use messages.",
            "Write progress notes of what was done, learned, and next steps.",
            "Claude's Full Response:",
            "Respond in this XML format:",
            "Thank you for your help!"
        );

        return new Mode("Code Development", "Software development and engineering work", "1.0.0", types, concepts, prompts);
    }

    /**
     * Get the configured mode ID from environment or settings.
     */
    private String getConfiguredModeId() {
        // Check environment variable first
        String envMode = System.getenv("CLAUDE_MEM_MODE");
        if (envMode != null && !envMode.isBlank()) {
            return envMode;
        }
        return configuredMode != null ? configuredMode : DEFAULT_MODE;
    }

    /**
     * Resolve the modes directory path.
     * Looks in multiple locations to support both development and production.
     */
    private Path resolveModesDir() {
        if (configuredModesDir != null && !configuredModesDir.isBlank()) {
            Path dir = Paths.get(configuredModesDir);
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }

        // Try common locations
        List<Path> possiblePaths = new ArrayList<>();

        // 1. Environment variable
        String envModesDir = System.getenv("CLAUDE_MEM_MODES_DIR");
        if (envModesDir != null) {
            possiblePaths.add(Paths.get(envModesDir));
        }

        // 2. User home plugin directory
        String homeDir = System.getProperty("user.home");
        if (homeDir != null) {
            possiblePaths.add(Paths.get(homeDir, ".claude", "plugins", "marketplaces", "thedotmack", "modes"));
        }

        // 3. Current working directory relative paths
        Path cwd = Paths.get("").toAbsolutePath();
        possiblePaths.add(cwd.resolve("plugin").resolve("modes"));
        possiblePaths.add(cwd.resolve("modes"));
        possiblePaths.add(cwd.getParent().resolve("plugin").resolve("modes"));

        for (Path path : possiblePaths) {
            if (Files.isDirectory(path)) {
                return path;
            }
        }

        // Default to cwd/plugin/modes (may not exist, will fallback to embedded)
        return cwd.resolve("plugin").resolve("modes");
    }

    /**
     * Parse mode ID for inheritance pattern (parent--override).
     */
    private InheritanceInfo parseInheritance(String modeId) {
        String[] parts = modeId.split("--");

        if (parts.length == 1) {
            return new InheritanceInfo(false, "", "");
        }

        if (parts.length > 2) {
            throw new IllegalArgumentException(
                "Invalid mode inheritance: " + modeId + ". Only one level of inheritance supported (parent--override)"
            );
        }

        return new InheritanceInfo(true, parts[0], modeId);
    }

    private record InheritanceInfo(boolean hasParent, String parentId, String overrideId) {}

    /**
     * Check if value is a plain object (not array, not null).
     */
    private boolean isPlainObject(JsonNode node) {
        return node != null && node.isObject();
    }

    /**
     * Deep merge two JsonNodes.
     * - Recursively merge nested objects
     * - Replace arrays completely (no merging)
     * - Override primitives
     */
    private JsonNode deepMerge(JsonNode base, JsonNode override) {
        if (!isPlainObject(base) || !isPlainObject(override)) {
            return override;
        }

        Map<String, JsonNode> result = new HashMap<>();
        base.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue()));

        override.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode overrideValue = entry.getValue();
            JsonNode baseValue = result.get(key);

            if (isPlainObject(overrideValue) && isPlainObject(baseValue)) {
                result.put(key, deepMerge(baseValue, overrideValue));
            } else {
                result.put(key, overrideValue);
            }
        });

        return objectMapper.valueToTree(result);
    }

    /**
     * Load a mode file from disk without inheritance processing.
     */
    private Mode loadModeFile(String modeId) {
        // Check cache first
        if (modeCache.containsKey(modeId)) {
            return modeCache.get(modeId);
        }

        Path modePath = modesDir.resolve(modeId + ".json");

        if (!Files.exists(modePath)) {
            // Try loading from classpath as fallback
            try {
                return loadModeFromClasspath(modeId);
            } catch (IOException e) {
                throw new IllegalArgumentException("Mode file not found: " + modePath);
            }
        }

        try {
            String jsonContent = Files.readString(modePath);
            Mode mode = objectMapper.readValue(jsonContent, Mode.class);
            modeCache.put(modeId, mode);
            return mode;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse mode file: " + modePath, e);
        }
    }

    /**
     * Load a mode from the classpath (embedded in JAR).
     */
    private Mode loadModeFromClasspath(String modeId) throws IOException {
        String resourcePath = "/modes/" + modeId + ".json";
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Mode resource not found: " + resourcePath);
            }
            String jsonContent = new String(is.readAllBytes());
            Mode mode = objectMapper.readValue(jsonContent, Mode.class);
            modeCache.put(modeId, mode);
            return mode;
        }
    }

    /**
     * Load a mode profile by ID with inheritance support.
     * Supports inheritance via parent--override pattern (e.g., code--es).
     *
     * @param modeId The mode ID to load
     * @return The loaded (and merged) mode configuration
     */
    public Mode loadMode(String modeId) {
        InheritanceInfo inheritance = parseInheritance(modeId);

        // No inheritance - load file directly
        if (!inheritance.hasParent()) {
            try {
                return loadModeFile(modeId);
            } catch (Exception e) {
                logHappyPath("Mode file not found: {}, falling back to 'code'", modeId);
                if (modeId.equals(DEFAULT_MODE)) {
                    logHappyPath("Using embedded default mode as fallback");
                    return createDefaultMode();
                }
                return loadMode(DEFAULT_MODE);
            }
        }

        String parentId = inheritance.parentId();
        String overrideId = inheritance.overrideId();

        Mode parentMode;
        try {
            parentMode = loadMode(parentId);
        } catch (Exception e) {
            logHappyPath("Parent mode '{}' not found for {}, falling back to 'code'", parentId, modeId);
            parentMode = loadMode(DEFAULT_MODE);
        }

        String overrideJson = null;
        Path overridePath = modesDir.resolve(overrideId + ".json");
        
        if (Files.exists(overridePath)) {
            try {
                overrideJson = Files.readString(overridePath);
                log.debug("Loaded override from filesystem: {}", overridePath);
            } catch (IOException e) {
                logHappyPath("Failed to read override file '{}': {}", overridePath, e.getMessage());
            }
        }
        
        if (overrideJson == null) {
            String resourcePath = "/modes/" + overrideId + ".json";
            try (var is = getClass().getResourceAsStream(resourcePath)) {
                if (is != null) {
                    overrideJson = new String(is.readAllBytes());
                    log.debug("Loaded override from classpath: {}", resourcePath);
                }
            } catch (IOException e) {
                log.debug("Override not found in classpath: {}", resourcePath);
            }
        }
        
        if (overrideJson == null) {
            logHappyPath("Override file '{}' not found (filesystem or classpath), using parent mode '{}' only", overrideId, parentId);
            return parentMode;
        }

        try {
            JsonNode overrideNode = objectMapper.readTree(overrideJson);
            JsonNode parentNode = objectMapper.valueToTree(parentMode);

            JsonNode mergedNode = deepMerge(parentNode, overrideNode);
            Mode mergedMode = objectMapper.treeToValue(mergedNode, Mode.class);

            logSuccess("Loaded mode with inheritance: {} ({} = {} + {})",
                mergedMode.name(), modeId, parentId, overrideId);

            modeCache.put(modeId, mergedMode);
            return mergedMode;
        } catch (IOException e) {
            logHappyPath("Failed to merge override file '{}': {}", overrideId, e.getMessage());
            return parentMode;
        }
    }

    /**
     * Get currently active mode.
     */
    public Mode getActiveMode() {
        if (activeMode == null) {
            throw new IllegalStateException("No mode loaded. Call loadMode() first.");
        }
        return activeMode;
    }

    public void setActiveMode(String modeId) {
        this.activeMode = loadMode(modeId);
        logSuccess("Active mode set to: {} ({})", activeMode.name(), modeId);
    }

    /**
     * Get all observation types from active mode.
     */
    public List<ObservationType> getObservationTypes() {
        return getActiveMode().observation_types();
    }

    /**
     * Get all observation concepts from active mode.
     */
    public List<ObservationConcept> getObservationConcepts() {
        return getActiveMode().observation_concepts();
    }

    /**
     * Get icon for a specific observation type.
     */
    public String getTypeEmoji(String typeId) {
        return getActiveMode().getTypeEmoji(typeId);
    }

    /**
     * Get work emoji for a specific observation type.
     */
    public String getWorkEmoji(String typeId) {
        return getActiveMode().getWorkEmoji(typeId);
    }

    /**
     * Get label for a specific observation type.
     */
    public String getTypeLabel(String typeId) {
        return getActiveMode().getTypeLabel(typeId);
    }

    /**
     * Validate that a type ID exists in the active mode.
     */
    public boolean isValidType(String typeId) {
        return getActiveMode().isValidType(typeId);
    }

    /**
     * Get all valid type IDs for the active mode.
     */
    public List<String> getValidTypeIds() {
        return getActiveMode().getTypeIds();
    }

    /**
     * Get all valid concept IDs for the active mode.
     */
    public List<String> getValidConceptIds() {
        return getActiveMode().getConceptIds();
    }

    /**
     * Get the modes directory path.
     */
    public Path getModesDir() {
        return modesDir;
    }

    /**
     * Get the currently configured mode ID.
     */
    public String getConfiguredMode() {
        return getConfiguredModeId();
    }
}
