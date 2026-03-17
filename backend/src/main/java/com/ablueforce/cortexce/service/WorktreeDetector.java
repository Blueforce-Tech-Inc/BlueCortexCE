package com.ablueforce.cortexce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git Worktree Detection Service.
 * <p>
 * Detects if the current working directory is a git worktree and extracts
 * information about the parent repository.
 * <p>
 * Git worktrees have a `.git` file (not directory) containing:
 * gitdir: /path/to/parent/.git/worktrees/&lt;name&gt;
 * <p>
 * Aligned with TS src/utils/worktree.ts
 */
@Service
public class WorktreeDetector {

    private static final Logger log = LoggerFactory.getLogger(WorktreeDetector.class);

    // Pattern to match gitdir line in .git file
    private static final Pattern GITDIR_PATTERN = Pattern.compile("^gitdir:\\s*(.+)$", Pattern.MULTILINE);

    // Pattern to extract parent path from gitdir path
    // Matches: /path/to/parent/.git/worktrees/name
    private static final Pattern WORKTREES_PATTERN = Pattern.compile("^(.+)[/\\\\]\\.git[/\\\\]worktrees[/\\\\]([^/\\\\]+)$");

    /**
     * Information about a git worktree.
     *
     * @param isWorktree        True if the directory is a worktree
     * @param worktreeName      Name of the worktree (e.g., "yokohama")
     * @param parentRepoPath    Path to the parent repository
     * @param parentProjectName Name of the parent project (basename of parent path)
     */
    public record WorktreeInfo(
        boolean isWorktree,
        String worktreeName,
        String parentRepoPath,
        String parentProjectName
    ) {
        /**
         * Constant for "not a worktree" result.
         */
        public static final WorktreeInfo NOT_A_WORKTREE = new WorktreeInfo(false, null, null, null);
    }

    /**
     * Project context with worktree awareness.
     *
     * @param primary     The current project name (worktree or main repo)
     * @param parent      Parent project name if in a worktree, null otherwise
     * @param isWorktree  True if currently in a worktree
     * @param allProjects All projects to query: [primary] for main repo, [parent, primary] for worktree
     */
    public record ProjectContext(
        String primary,
        String parent,
        boolean isWorktree,
        List<String> allProjects
    ) {}

    /**
     * Detect if a directory is a git worktree and extract parent info.
     *
     * @param cwd Current working directory (absolute path)
     * @return WorktreeInfo with parent details if worktree, otherwise isWorktree=false
     */
    public WorktreeInfo detectWorktree(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return WorktreeInfo.NOT_A_WORKTREE;
        }

        Path gitPath = Paths.get(cwd, ".git");

        // Check if .git exists
        if (!Files.exists(gitPath)) {
            return WorktreeInfo.NOT_A_WORKTREE;
        }

        // Check if .git is a file (worktree) or directory (main repo)
        if (Files.isDirectory(gitPath)) {
            // .git is a directory = main repo, not a worktree
            return WorktreeInfo.NOT_A_WORKTREE;
        }

        // .git is a file - parse it to find parent repo
        String content;
        try {
            content = Files.readString(gitPath).trim();
        } catch (IOException e) {
            log.warn("Failed to read .git file: {}", gitPath);
            return WorktreeInfo.NOT_A_WORKTREE;
        }

        // Format: gitdir: /path/to/parent/.git/worktrees/<name>
        Matcher gitdirMatcher = GITDIR_PATTERN.matcher(content);
        if (!gitdirMatcher.find()) {
            return WorktreeInfo.NOT_A_WORKTREE;
        }

        String gitdirPath = gitdirMatcher.group(1);

        // Extract: /path/to/parent from /path/to/parent/.git/worktrees/name
        Matcher worktreesMatcher = WORKTREES_PATTERN.matcher(gitdirPath);
        if (!worktreesMatcher.matches()) {
            return WorktreeInfo.NOT_A_WORKTREE;
        }

        String parentRepoPath = worktreesMatcher.group(1);
        String worktreeName = Paths.get(cwd).getFileName().toString();
        String parentProjectName = Paths.get(parentRepoPath).getFileName().toString();

        log.debug("Detected worktree: {} -> parent: {}", worktreeName, parentProjectName);

        return new WorktreeInfo(true, worktreeName, parentRepoPath, parentProjectName);
    }

    /**
     * Get project context with worktree detection.
     * <p>
     * When in a worktree, returns both the worktree project name and parent project name
     * for unified timeline queries.
     *
     * @param cwd Current working directory (absolute path)
     * @return ProjectContext with worktree info
     */
    public ProjectContext getProjectContext(String cwd) {
        String primary = getProjectName(cwd);

        if (cwd == null || cwd.isBlank()) {
            return new ProjectContext(primary, null, false, List.of(primary));
        }

        WorktreeInfo worktreeInfo = detectWorktree(cwd);

        if (worktreeInfo.isWorktree() && worktreeInfo.parentProjectName() != null) {
            // In a worktree: include parent first for chronological ordering
            return new ProjectContext(
                primary,
                worktreeInfo.parentProjectName(),
                true,
                List.of(worktreeInfo.parentProjectName(), primary)
            );
        }

        return new ProjectContext(primary, null, false, List.of(primary));
    }

    /**
     * Extract project name from working directory path.
     * Handles edge cases: null/undefined cwd, drive roots, trailing slashes.
     *
     * @param cwd Current working directory (absolute path)
     * @return Project name or "unknown-project" if extraction fails
     */
    public String getProjectName(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            log.warn("Empty cwd provided, using fallback");
            return "unknown-project";
        }

        // Extract basename (handles trailing slashes automatically)
        Path path = Paths.get(cwd);
        String basename = path.getFileName() != null ? path.getFileName().toString() : "";

        // Edge case: Root directory
        if (basename.isEmpty()) {
            log.warn("Root directory detected, using fallback: {}", cwd);
            return "unknown-project";
        }

        return basename;
    }
}
