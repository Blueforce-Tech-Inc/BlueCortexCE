package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating CLAUDE.md content from observations.
 * <p>
 * In the thin-proxy architecture, this service only generates content strings.
 * File I/O is handled by the proxy layer (wrapper.js).
 */
@Service
public class ClaudeMdService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeMdService.class);
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ObservationRepository observationRepository;

    public ClaudeMdService(ObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
    }

    /**
     * Generate CLAUDE.md content for a project.
     *
     * @param projectPath the project path to generate CLAUDE.md for
     * @return CLAUDE.md content string
     */
    public String generateClaudeMd(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return "# Claude-Mem Context\n\nNo project path specified.\n";
        }

        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# Claude-Mem Context\n\n");
        sb.append("Generated: ").append(DATE_FORMAT.format(Instant.now())).append("\n\n");

        // Recent observations (paged query avoids loading all into memory)
        Page<ObservationEntity> page = observationRepository
            .findByProjectPathOrderByCreatedAtDesc(projectPath, PageRequest.of(0, 10));
        List<ObservationEntity> recentObs = page.getContent();

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

        // Summary statistics (reuse page total count to avoid duplicate query)
        long totalObs = page.getTotalElements();
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
        if (projectPath == null || projectPath.isBlank()) {
            return new ProjectMemorySummary(projectPath, 0, List.of());
        }

        Page<ObservationEntity> page = observationRepository
            .findByProjectPathOrderByCreatedAtDesc(projectPath, PageRequest.of(0, 5));

        return new ProjectMemorySummary(projectPath, page.getTotalElements(), page.getContent());
    }

    /**
     * Project memory summary record.
     */
    public record ProjectMemorySummary(
        String projectPath,
        long totalObservations,
        List<ObservationEntity> recentObservations
    ) {}
}
