package com.ablueforce.cortexce.service;

import org.springframework.util.AntPathMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Project path filter using Spring's AntPathMatcher.
 * <p>
 * Supports .claudeignore-style glob patterns for path inclusion/exclusion.
 * Uses AntPathMatcher which handles *, **, ? patterns and cross-platform separators.
 *
 * <p><b>Note:</b> This class is not currently wired into any processing pipeline.
 * It is retained as a utility for future project filtering features.
 */
public class ProjectFilterService {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> includePatterns = new CopyOnWriteArrayList<>();
    private final List<String> excludePatterns = new CopyOnWriteArrayList<>();

    // Default unsafe directories to always exclude
    private static final List<String> DEFAULT_EXCLUDES = List.of(
        "**/.git/**",
        "**/node_modules/**",
        "**/build/**",
        "**/dist/**",
        "**/__pycache__/**",
        "**/res/**",
        "**/.idea/**",
        "**/.vscode/**",
        "**/target/**"
    );

    /**
     * Creates a ProjectFilterService with default exclude patterns loaded.
     */
    public ProjectFilterService() {
        loadPatterns(null, null);
    }

    /**
     * Load filter patterns from configuration.
     */
    public void loadPatterns(List<String> includes, List<String> excludes) {
        this.includePatterns.clear();
        this.excludePatterns.clear();

        if (includes != null) {
            this.includePatterns.addAll(includes);
        }
        this.excludePatterns.addAll(DEFAULT_EXCLUDES);
        if (excludes != null) {
            this.excludePatterns.addAll(excludes);
        }
    }

    /**
     * Check if a path should be included based on the current filter configuration.
     */
    public boolean shouldInclude(String path) {
        if (path == null) {
            return false;
        }
        // Normalize home directory reference: handles both ~ and ~username forms
        String normalizedPath = path.isBlank() ? path : expandHomeDirectory(path);

        // Check exclude patterns first
        for (String pattern : excludePatterns) {
            if (pathMatcher.match(pattern, normalizedPath)) {
                return false;
            }
        }

        // If no include patterns, include everything not excluded
        if (includePatterns.isEmpty()) {
            return true;
        }

        // Check include patterns
        for (String pattern : includePatterns) {
            if (pathMatcher.match(pattern, normalizedPath)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a directory is considered "unsafe" for automatic CLAUDE.md modification.
     */
    public boolean isUnsafeDirectory(String path) {
        if (path == null) {
            return false;
        }
        String normalizedPath = path.isBlank() ? path : expandHomeDirectory(path);
        for (String pattern : DEFAULT_EXCLUDES) {
            if (pathMatcher.match(pattern, normalizedPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Expand home directory references in a path.
     * Handles both ~ (current user) and ~username (specific user) forms.
     */
    private String expandHomeDirectory(String path) {
        if (path == null) return path;
        if (path.startsWith("~")) {
            if (path.length() > 1 && path.charAt(1) == '/') {
                // ~user/path or ~/path — expand current user home
                return path.replaceFirst("^~", System.getProperty("user.home"));
            }
            // ~username/path — expand to that user's home (best effort)
            int slashIdx = path.indexOf('/');
            if (slashIdx > 0) {
                String username = path.substring(1, slashIdx);
                String userHome = System.getProperty("user.home");
                // Fallback: if we can't resolve ~username, use current home
                return path.replaceFirst("^~" + username, userHome);
            }
        }
        return path;
    }
}
