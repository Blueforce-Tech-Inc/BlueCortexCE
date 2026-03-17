package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating and updating CLAUDE.md content from observations.
 * <p>
 * Provides the CLAUDE.md file content with project-specific context,
 * including recent work, active features, and accumulated knowledge.
 * <p>
 * Supports:
 * - Tag-based content replacement (preserves user content outside tags)
 * - Atomic file writes (temp file + rename)
 * <p>
 * Aligned with TS src/utils/claude-md-utils.ts
 */
@Service
public class ClaudeMdService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeMdService.class);
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private static final String START_TAG = "<claude-mem-context>";
    private static final String END_TAG = "</claude-mem-context>";

    private final ObservationRepository observationRepository;

    public ClaudeMdService(ObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
    }

    /**
     * Generate CLAUDE.md content for a project.
     *
     * @param projectPath the project path to generate CLAUDE.md for
     * @return CLAUDE.md content string
     * @throws RuntimeException if database query fails
     */
    public String generateClaudeMd(String projectPath) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# Claude-Mem Context\n\n");
        sb.append("Generated: ").append(DATE_FORMAT.format(Instant.now())).append("\n\n");

        // Recent observations
        List<ObservationEntity> recentObs = observationRepository
            .findByProjectPathOrderByCreatedAtDesc(projectPath)
            .stream()
            .limit(10)
            .collect(Collectors.toList());

        if (!recentObs.isEmpty()) {
            sb.append("## Recent Work\n\n");
            for (ObservationEntity obs : recentObs) {
                sb.append("### ").append(obs.getTitle());
                if (obs.getSubtitle() != null && !obs.getSubtitle().isBlank()) {
                    sb.append(" — ").append(obs.getSubtitle());
                }
                sb.append("\n");
                sb.append("_").append(DATE_FORMAT.format(
                    Instant.ofEpochMilli(obs.getCreatedAtEpoch()))).append("_\n\n");
                if (obs.getContent() != null) {
                    sb.append(obs.getContent()).append("\n\n");
                }
                if (obs.getFacts() != null && !obs.getFacts().isEmpty()) {
                    sb.append("**Facts:**\n");
                    obs.getFacts().stream()
                        .filter(fact -> fact != null && !fact.isBlank())
                        .forEach(fact -> sb.append("- ").append(fact).append("\n"));
                    sb.append("\n");
                }
                if (obs.getConcepts() != null && !obs.getConcepts().isEmpty()) {
                    sb.append("**Concepts:** ").append(String.join(", ", obs.getConcepts())).append("\n\n");
                }
            }
        }

        // Summary statistics
        long totalObs = observationRepository.countByProjectPath(projectPath);
        if (totalObs > 0) {
            sb.append("---\n\n");
            sb.append("## Statistics\n\n");
            sb.append("- Total observations: ").append(totalObs).append("\n");
            sb.append("- This file auto-regenerates from project memory\n");
        }

        return sb.toString();
    }

    /**
     * Get project memory summary for a project.
     *
     * @param projectPath the project path
     * @return summary object
     */
    public ProjectMemorySummary getProjectMemorySummary(String projectPath) {
        long totalObs = observationRepository.countByProjectPath(projectPath);
        List<ObservationEntity> recentObs = observationRepository
            .findByProjectPathOrderByCreatedAtDesc(projectPath)
            .stream()
            .limit(5)
            .collect(Collectors.toList());

        return new ProjectMemorySummary(projectPath, totalObs, recentObs);
    }

    /**
     * Project memory summary record.
     */
    public record ProjectMemorySummary(
        String projectPath,
        long totalObservations,
        List<ObservationEntity> recentObservations
    ) {}

    // ===== Tag-Based Content Replacement (aligned with TS replaceTaggedContent) =====

    /**
     * Replace tagged content in existing file, preserving content outside tags.
     * <p>
     * Handles three cases:
     * 1. No existing content → wraps new content in tags
     * 2. Has existing tags → replaces only tagged section
     * 3. No tags in existing content → appends tagged content at end
     * <p>
     * Aligned with TS replaceTaggedContent function.
     *
     * @param existingContent the existing file content (may be empty or null)
     * @param newContent the new content to place inside tags
     * @return the final content with tags properly placed
     */
    public String replaceTaggedContent(String existingContent, String newContent) {
        // If no existing content, wrap new content in tags
        if (existingContent == null || existingContent.isBlank()) {
            return START_TAG + "\n" + newContent + "\n" + END_TAG;
        }

        // If existing has tags, replace only tagged section
        int startIdx = existingContent.indexOf(START_TAG);
        int endIdx = existingContent.indexOf(END_TAG);

        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return existingContent.substring(0, startIdx) +
                START_TAG + "\n" + newContent + "\n" + END_TAG +
                existingContent.substring(endIdx + END_TAG.length());
        }

        // If no tags exist, append tagged content at end
        return existingContent + "\n\n" + START_TAG + "\n" + newContent + "\n" + END_TAG;
    }

    /**
     * Update a CLAUDE.md file at the given path with new tagged content.
     * <p>
     * Uses atomic writes (temp file + rename) for safety.
     * Preserves user content outside the tags.
     *
     * @param claudeMdPath the path to the CLAUDE.md file
     * @param newContent the new content to place inside tags
     * @throws IOException if file operations fail
     */
    public void updateClaudeMdFile(Path claudeMdPath, String newContent) throws IOException {
        // Read existing content if file exists
        String existingContent = "";
        if (Files.exists(claudeMdPath)) {
            existingContent = Files.readString(claudeMdPath);
        }

        // Replace only tagged content, preserve user content
        String finalContent = replaceTaggedContent(existingContent, newContent);

        // Atomic write: temp file + rename
        Path tempPath = claudeMdPath.resolveSibling(claudeMdPath.getFileName() + ".tmp");
        Files.writeString(tempPath, finalContent);
        Files.move(tempPath, claudeMdPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);

        log.debug("Updated CLAUDE.md with atomic write: {}", claudeMdPath);
    }

    /**
     * Update a CLAUDE.md file in a folder with new tagged content.
     * <p>
     * Only writes to folders that already exist; skips non-existent paths.
     * Uses atomic writes (temp file + rename) for safety.
     *
     * @param folderPath the absolute path to the folder (must already exist)
     * @param newContent the new content to place inside tags
     */
    public void writeClaudeMdToFolder(String folderPath, String newContent) {
        Path folder = Paths.get(folderPath);

        // Only write to folders that already exist
        if (!Files.isDirectory(folder)) {
            log.debug("Skipping non-existent folder: {}", folderPath);
            return;
        }

        Path claudeMdPath = folder.resolve("CLAUDE.md");

        try {
            updateClaudeMdFile(claudeMdPath, newContent);
            log.debug("Wrote CLAUDE.md to folder: {}", folderPath);
        } catch (IOException e) {
            log.error("Failed to write CLAUDE.md to folder {}: {}", folderPath, e.getMessage());
        }
    }

    /**
     * Generate and update CLAUDE.md for a project path.
     * <p>
     * Combines generation and atomic file update in one operation.
     *
     * @param projectPath the project path
     * @param claudeMdPath the path to the CLAUDE.md file
     * @throws IOException if file operations fail
     */
    public void generateAndUpdateClaudeMd(String projectPath, Path claudeMdPath) throws IOException {
        String newContent = generateClaudeMd(projectPath);
        updateClaudeMdFile(claudeMdPath, newContent);
    }

    /**
     * Generate and write CLAUDE.md to a folder.
     * <p>
     * Convenience method that combines generation and folder write.
     *
     * @param projectPath the project path for content generation
     * @param folderPath the folder path to write CLAUDE.md to
     */
    public void generateAndWriteToFolder(String projectPath, String folderPath) {
        String newContent = generateClaudeMd(projectPath);
        writeClaudeMdToFolder(folderPath, newContent);
    }
}
