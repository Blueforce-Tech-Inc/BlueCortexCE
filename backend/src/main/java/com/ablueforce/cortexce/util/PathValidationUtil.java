package com.ablueforce.cortexce.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared path validation utilities for controllers.
 * Extracted from SessionController and ContextController to eliminate code duplication (~70 lines).
 */
public final class PathValidationUtil {

    private static final Logger log = LoggerFactory.getLogger(PathValidationUtil.class);

    private static final int MAX_CLAUDE_MD_DEPTH = 10;

    private PathValidationUtil() {
        // Utility class — no instantiation
    }

    /**
     * Find CLAUDE.md in a project directory by checking the project root first,
     * then traversing parent directories up to MAX_DEPTH.
     *
     * @param projectPath the project root path
     * @return Path to the found CLAUDE.md, or null if not found
     */
    public static Path findClaudeMdInProject(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }

        // Normalize and resolve the path to prevent path traversal
        Path basePath = Paths.get(projectPath).toAbsolutePath().normalize();
        Path claudeMdPath = basePath.resolve("CLAUDE.md");

        // Validate the resolved path is within the project
        if (isWithinProject(basePath, claudeMdPath)) {
            try {
                if (Files.exists(claudeMdPath)) {
                    return claudeMdPath;
                }
            } catch (SecurityException e) {
                log.warn("Cannot access CLAUDE.md at {}: {}", claudeMdPath, e.getMessage());
            }
        }

        // Search parent directories but limit to reasonable depth and validate bounds
        Path rootPath = basePath.getRoot();
        Path current = basePath.getParent();
        int depth = 0;

        while (current != null && !current.equals(rootPath) && depth < MAX_CLAUDE_MD_DEPTH) {
            Path candidate = current.resolve("CLAUDE.md");

            // Validate candidate is still within the filesystem bounds
            if (!isWithinProject(basePath, candidate)) {
                log.warn("Path traversal attempt detected: {} would escape project", candidate);
                return null;
            }

            try {
                if (Files.exists(candidate)) {
                    return candidate;
                }
            } catch (SecurityException e) {
                log.warn("Cannot access candidate path {}: {}", candidate, e.getMessage());
            }

            // Stop at project root (.git directory)
            try {
                Path gitPath = current.resolve(".git");
                if (Files.exists(gitPath)) {
                    return null;
                }
            } catch (SecurityException e) {
                log.warn("Cannot access .git at {}: {}", current, e.getMessage());
            }

            current = current.getParent();
            depth++;
        }

        return null;
    }

    /**
     * Check if the target path is within the project boundaries.
     * Prevents path traversal via symlinks or relative path components.
     *
     * @param projectRoot the project root directory
     * @param targetPath  the path to check
     * @return true if target is within (or equal to) project root
     */
    public static boolean isWithinProject(Path projectRoot, Path targetPath) {
        try {
            Path normalizedTarget = targetPath.toAbsolutePath().normalize();
            Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
            return normalizedTarget.startsWith(normalizedRoot)
                || normalizedTarget.equals(normalizedRoot);
        } catch (SecurityException e) {
            log.warn("Path security check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a directory path is safe for operations.
     * Verifies the path exists, is a directory, and is not a sensitive system path.
     *
     * @param path the directory path to check
     * @return true if the directory is safe to operate on
     */
    public static boolean isSafeDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            Path dir = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                return false;
            }
            // Block sensitive system directories
            String normalized = dir.toString();
            if (normalized.equals("/") || normalized.equals("/etc") ||
                normalized.equals("/usr") || normalized.equals("/var") ||
                normalized.equals("/sys") || normalized.equals("/proc")) {
                return false;
            }
            return dir.getNameCount() > 0;
        } catch (Exception e) {
            log.warn("Safety check failed for path {}: {}", path, e.getMessage());
            return false;
        }
    }
}
