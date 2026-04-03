package com.ablueforce.cortexce.service;

import org.springframework.util.AntPathMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
    private final List<String> includePatterns = new ArrayList<>();
    private final List<String> excludePatterns = new ArrayList<>();

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
        if (path == null || path.isBlank()) {
            return false;
        }
        // Normalize home directory reference
        String normalizedPath = path.startsWith("~")
            ? path.replaceFirst("^~", System.getProperty("user.home"))
            : path;

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
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String pattern : DEFAULT_EXCLUDES) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
