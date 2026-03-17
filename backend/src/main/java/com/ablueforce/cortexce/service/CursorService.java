package com.ablueforce.cortexce.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CursorService - Cursor IDE integration for claude-mem Java Port.
 * <p>
 * Handles:
 * - Project registry management (for auto-context updates)
 * - Context file generation for Cursor rules
 * <p>
 * Aligned with TS src/services/integrations/CursorHooksInstaller.ts
 * <p>
 * Note: Hook installation is handled by the TS proxy layer, not Java backend.
 * This service only manages the project registry and context updates.
 */
@Service
public class CursorService {

    private static final Logger log = LoggerFactory.getLogger(CursorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${claudemem.data-dir:#{systemProperties['user.home'] + '/.claude-mem'}}")
    private String dataDir;

    // In-memory cache of the registry
    private final Map<String, CursorProjectEntry> registryCache = new ConcurrentHashMap<>();

    /**
     * Project entry in the registry.
     */
    public record CursorProjectEntry(
        String workspacePath,
        String installedAt
    ) {}

    /**
     * Get the registry file path.
     */
    private Path getRegistryPath() {
        return Paths.get(dataDir, "cursor-projects.json");
    }

    /**
     * Read the Cursor project registry from disk.
     */
    public Map<String, CursorProjectEntry> readRegistry() {
        Path registryPath = getRegistryPath();

        // Try cache first
        if (!registryCache.isEmpty()) {
            return new HashMap<>(registryCache);
        }

        if (!Files.exists(registryPath)) {
            return new HashMap<>();
        }

        try {
            String content = Files.readString(registryPath);
            Map<String, CursorProjectEntry> registry = MAPPER.readValue(content,
                new TypeReference<Map<String, CursorProjectEntry>>() {});

            // Update cache
            registryCache.clear();
            registryCache.putAll(registry);

            return registry;
        } catch (IOException e) {
            log.error("Failed to read cursor registry: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Write the Cursor project registry to disk.
     */
    public void writeRegistry(Map<String, CursorProjectEntry> registry) {
        Path registryPath = getRegistryPath();

        try {
            // Ensure directory exists
            Files.createDirectories(registryPath.getParent());

            // Write to file
            String content = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(registry);
            Files.writeString(registryPath, content);

            // Update cache
            registryCache.clear();
            registryCache.putAll(registry);

            log.debug("Wrote cursor registry with {} entries", registry.size());
        } catch (IOException e) {
            log.error("Failed to write cursor registry: {}", e.getMessage());
        }
    }

    /**
     * Register a project for auto-context updates.
     *
     * @param projectName  The project name (usually basename of workspace)
     * @param workspacePath The absolute path to the workspace
     */
    public void registerProject(String projectName, String workspacePath) {
        Map<String, CursorProjectEntry> registry = readRegistry();

        registry.put(projectName, new CursorProjectEntry(
            workspacePath,
            Instant.now().toString()
        ));

        writeRegistry(registry);
        log.info("Registered Cursor project: {} -> {}", projectName, workspacePath);
    }

    /**
     * Unregister a project from auto-context updates.
     *
     * @param projectName The project name to unregister
     * @return true if project was unregistered, false if it wasn't registered
     */
    public boolean unregisterProject(String projectName) {
        Map<String, CursorProjectEntry> registry = readRegistry();

        if (registry.containsKey(projectName)) {
            registry.remove(projectName);
            writeRegistry(registry);
            log.info("Unregistered Cursor project: {}", projectName);
            return true;
        }

        return false;
    }

    /**
     * Get a registered project entry.
     *
     * @param projectName The project name
     * @return The entry or null if not registered
     */
    public CursorProjectEntry getProject(String projectName) {
        return readRegistry().get(projectName);
    }

    /**
     * Check if a project is registered.
     *
     * @param projectName The project name
     * @return true if registered
     */
    public boolean isProjectRegistered(String projectName) {
        return readRegistry().containsKey(projectName);
    }

    /**
     * Get all registered projects.
     *
     * @return Map of project name to entry
     */
    public Map<String, CursorProjectEntry> getAllProjects() {
        return readRegistry();
    }

    /**
     * Update the Cursor context file for a project.
     * <p>
     * Writes context to .cursor/rules/claude-mem-context.mdc
     *
     * @param projectName The project name
     * @param context     The context content to write
     * @return true if successful, false otherwise
     */
    public boolean updateContextFile(String projectName, String context) {
        CursorProjectEntry entry = getProject(projectName);

        if (entry == null) {
            log.debug("Project {} not registered for Cursor context updates", projectName);
            return false;
        }

        return writeContextFile(entry.workspacePath(), context);
    }

    /**
     * Write context file to a workspace.
     * <p>
     * Creates .cursor/rules/claude-mem-context.mdc with frontmatter.
     *
     * @param workspacePath The workspace path
     * @param context       The context content
     * @return true if successful
     */
    public boolean writeContextFile(String workspacePath, String context) {
        try {
            Path cursorDir = Paths.get(workspacePath, ".cursor");
            Path rulesDir = cursorDir.resolve("rules");

            // Ensure directory exists
            Files.createDirectories(rulesDir);

            // Write context file with frontmatter
            Path contextFile = rulesDir.resolve("claude-mem-context.mdc");

            StringBuilder content = new StringBuilder();
            content.append("---\n");
            content.append("alwaysApply: true\n");
            content.append("description: \"Claude-mem context from past sessions (auto-updated)\"\n");
            content.append("---\n\n");
            content.append(context);

            Files.writeString(contextFile, content.toString());

            log.info("Updated Cursor context file: {}", contextFile);
            return true;
        } catch (IOException e) {
            log.error("Failed to write Cursor context file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Clear the registry cache (for testing).
     */
    public void clearCache() {
        registryCache.clear();
    }
}
