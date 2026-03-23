package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for ExpRAG-style experience retrieval.
 * 
 * Based on Evo-Memory paper Section 6.2.2 - Experience Retrieval Enhancement.
 * 
 * Retrieves experiences as ICL (In-Context Learning) samples,
 * prioritizing high-quality experiences for better context.
 */
@Service
public class ExpRagService {

    private static final Logger log = LoggerFactory.getLogger(ExpRagService.class);

    // Configuration
    private static final float MIN_QUALITY_THRESHOLD = 0.6f;
    private static final int DEFAULT_RETRIEVAL_COUNT = 4;

    private final ObservationRepository observationRepository;
    private final SessionRepository sessionRepository;

    public ExpRagService(ObservationRepository observationRepository, SessionRepository sessionRepository) {
        this.observationRepository = observationRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Retrieve relevant experiences for ICL-style context.
     * Prioritizes high-quality experiences.
     * 
     * @param currentTask Current task description
     * @param projectPath Project path
     * @return List of experiences formatted for ICL
     */
    public List<Experience> retrieveExperiences(String currentTask, String projectPath) {
        return retrieveExperiences(currentTask, projectPath, DEFAULT_RETRIEVAL_COUNT);
    }

    /**
     * Retrieve experiences with custom count using high-quality filter.
     */
    public List<Experience> retrieveExperiences(String currentTask, String projectPath, int count) {
        return retrieveExperiences(currentTask, projectPath, count, null, null);
    }

    /**
     * Retrieve experiences with filters.
     *
     * @param currentTask Task description (currently unused for filtering, used for future semantic search)
     * @param projectPath Project path
     * @param count Number of experiences to retrieve
     * @param source Optional source filter (e.g., "tool_result", "user_statement")
     * @param requiredConcepts Optional concept filter (must contain all specified concepts)
     */
    public List<Experience> retrieveExperiences(String currentTask, String projectPath, int count,
                                                String source, List<String> requiredConcepts) {
        List<ObservationEntity> results;

        if (source != null && !source.isBlank()) {
            // Use source-based repository method (fetch extra to reduce need for fallback)
            results = observationRepository.findBySource(projectPath, source, count * 3);
        } else {
            // Use quality-aware repository method
            results = observationRepository
                .findHighQualityObservations(projectPath, MIN_QUALITY_THRESHOLD, count * 3);
        }

        // If not enough, get recent observations (respect source filter if active)
        if (results.size() < count) {
            List<ObservationEntity> recent;
            if (source != null && !source.isBlank()) {
                // CRITICAL: fallback must also respect source filter
                recent = observationRepository.findBySource(projectPath, source, count);
            } else {
                recent = observationRepository.findByProjectLimited(projectPath, count);
            }

            // Merge, remove duplicates
            results.addAll(recent);
            results = results.stream()
                .distinct()
                .toList();
        }

        // Filter by required concepts if specified
        if (requiredConcepts != null && !requiredConcepts.isEmpty()) {
            final List<String> conceptsToMatch = requiredConcepts;
            results = results.stream()
                .filter(obs -> {
                    List<String> obsConcepts = obs.getConcepts();
                    if (obsConcepts == null || obsConcepts.isEmpty()) return false;
                    // Check if all required concepts are present
                    return conceptsToMatch.stream().allMatch(obsConcepts::contains);
                })
                .toList();
        }

        // Limit to count
        results = results.stream().limit(count).toList();

        // Convert to experience format
        return results.stream()
            .map(this::toExperience)
            .toList();
    }

    /**
     * Build ICL prompt from retrieved experiences.
     * @deprecated Use {@link #buildICLPrompt(String, List, int)} with maxChars for adaptive truncation
     */
    @Deprecated
    public String buildICLPrompt(String currentTask, List<Experience> experiences) {
        return buildICLPrompt(currentTask, experiences, 4000);
    }

    /**
     * Build ICL prompt from retrieved experiences with adaptive truncation.
     *
     * @param currentTask The current task description
     * @param experiences Retrieved experiences to include
     * @param maxChars Maximum characters for the ICL prompt. If exceeded, truncates experiences.
     * @return Formatted ICL prompt string
     */
    public String buildICLPrompt(String currentTask, List<Experience> experiences, int maxChars) {
        if (experiences.isEmpty()) {
            return "Current task:\n" + currentTask;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Relevant historical experiences:\n\n");

        for (int i = 0; i < experiences.size(); i++) {
            Experience exp = experiences.get(i);
            String expBlock = String.format("### Experience %d\n**Task**: %s\n**Strategy**: %s\n**Outcome**: %s\n**Quality**: %.2f\n\n",
                i + 1, exp.task(), exp.strategy(), exp.outcome(), exp.qualityScore());
            
            // Check if adding this experience would exceed maxChars
            if (sb.length() + expBlock.length() + currentTask.length() + 50 > maxChars) {
                log.debug("ICL prompt truncated at experience {} to stay within {} char limit", i, maxChars);
                break;
            }
            
            sb.append(expBlock);
        }

        sb.append("---\n\n");
        sb.append("Current task:\n");
        
        // Final truncation check for currentTask
        String currentTaskBlock = currentTask;
        if (sb.length() + currentTaskBlock.length() > maxChars) {
            currentTaskBlock = currentTaskBlock.substring(0, Math.max(0, maxChars - sb.length() - 10)) + "...";
            log.debug("Current task truncated to fit within {} char limit", maxChars);
        }
        
        sb.append(currentTaskBlock);

        return sb.toString();
    }

    /**
     * Convert ObservationEntity to Experience format.
     */
    private Experience toExperience(ObservationEntity obs) {
        String content = obs.getContent();
        String title = obs.getTitle();

        return new Experience(
            obs.getId().toString(),
            title != null ? title : extractTaskFromContent(content),
            extractStrategyFromContent(content),
            extractOutcomeFromContent(content),
            extractReuseCondition(obs),
            obs.getQualityScore() != null ? obs.getQualityScore() : 0.5f,
            obs.getCreatedAt()
        );
    }

    private String extractTaskFromContent(String content) {
        if (content == null) return "Unknown task";
        
        int taskStart = content.indexOf("## Task");
        if (taskStart >= 0) {
            int sectionEnd = content.indexOf("\n##", taskStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            return content.substring(taskStart + 8, sectionEnd).trim();
        }
        
        return content.length() > 100 ? content.substring(0, 100) : content;
    }

    private String extractStrategyFromContent(String content) {
        if (content == null) return "N/A";
        
        int strategyStart = content.indexOf("## Reasoning");
        if (strategyStart < 0) strategyStart = content.indexOf("## Strategy");
        
        if (strategyStart >= 0) {
            int sectionEnd = content.indexOf("\n##", strategyStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            return content.substring(strategyStart + 12, sectionEnd).trim();
        }
        
        return "General approach used";
    }

    private String extractOutcomeFromContent(String content) {
        if (content == null) return "N/A";
        
        int outcomeStart = content.indexOf("## Outcome");
        if (outcomeStart >= 0) {
            int sectionEnd = content.indexOf("\n##", outcomeStart + 2);
            if (sectionEnd < 0) sectionEnd = content.length();
            return content.substring(outcomeStart + 11, sectionEnd).trim();
        }
        
        return "Task completed";
    }

    private String extractReuseCondition(ObservationEntity obs) {
        StringBuilder sb = new StringBuilder();
        
        if (obs.getTitle() != null) {
            sb.append("When ").append(obs.getTitle().toLowerCase());
        } else {
            sb.append("When similar task");
        }
        
        if (obs.getType() != null) {
            sb.append(" (").append(obs.getType()).append(")");
        }
        
        return sb.toString();
    }

    /**
     * Retrieve experiences with userId filtering (Phase 3 multi-user support).
     *
     * @param currentTask Task description
     * @param projectPath Project path
     * @param count Number of experiences to retrieve
     * @param userId User ID to filter by (optional, null means no filtering)
     * @param source Optional source filter
     * @param requiredConcepts Optional concept filter
     * @return List of experiences
     */
    public List<Experience> retrieveExperiences(String currentTask, String projectPath, int count,
                                                String userId, String source, List<String> requiredConcepts) {
        // If userId is provided, filter by user's sessions
        if (userId != null && !userId.isBlank()) {
            List<String> sessionIds = sessionRepository.findSessionIdsByUserIdAndProject(userId, projectPath);
            if (sessionIds.isEmpty()) {
                log.debug("No sessions found for userId={} in project={}", userId, projectPath);
                return List.of();
            }

            // Get observations from user's sessions (batch query instead of N+1)
            List<ObservationEntity> results = new ArrayList<>(
                observationRepository.findByContentSessionIdInOrderByCreatedAtEpochDesc(sessionIds)
            );

            // Apply source filter if specified
            if (source != null && !source.isBlank()) {
                results = results.stream()
                    .filter(obs -> source.equals(obs.getSource()))
                    .toList();
            }

            // Apply concept filter if specified
            if (requiredConcepts != null && !requiredConcepts.isEmpty()) {
                final List<String> conceptsToMatch = requiredConcepts;
                results = results.stream()
                    .filter(obs -> {
                        if (obs.getConcepts() == null) return false;
                        return conceptsToMatch.stream()
                            .allMatch(concept -> obs.getConcepts().contains(concept));
                    })
                    .toList();
            }

            // Sort by quality and recency, limit to count
            results = results.stream()
                .sorted((a, b) -> {
                    int qualityCompare = Double.compare(
                        b.getQualityScore() != null ? b.getQualityScore() : 0.0,
                        a.getQualityScore() != null ? a.getQualityScore() : 0.0
                    );
                    if (qualityCompare != 0) return qualityCompare;
                    return Long.compare(b.getCreatedAtEpoch(), a.getCreatedAtEpoch());
                })
                .limit(count)
                .toList();

            return results.stream()
                .map(this::toExperience)
                .toList();
        }

        // No userId, use existing method
        return retrieveExperiences(currentTask, projectPath, count, source, requiredConcepts);
    }

    /**
     * Experience record for ICL context.
     */
    public record Experience(
        String id,
        String task,
        String strategy,
        String outcome,
        String reuseCondition,
        float qualityScore,
        java.time.OffsetDateTime createdAt
    ) {}
}
