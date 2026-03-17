package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public ExpRagService(ObservationRepository observationRepository) {
        this.observationRepository = observationRepository;
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
        // Use quality-aware repository method
        List<ObservationEntity> highQuality = observationRepository
            .findHighQualityObservations(projectPath, MIN_QUALITY_THRESHOLD, count * 3);

        // If not enough, get recent observations
        if (highQuality.size() < count) {
            List<ObservationEntity> recent = observationRepository
                .findByProjectLimited(projectPath, count);
            
            // Merge, remove duplicates, limit
            highQuality.addAll(recent);
            highQuality = highQuality.stream()
                .distinct()
                .limit(count)
                .toList();
        }

        // Convert to experience format
        return highQuality.stream()
            .map(this::toExperience)
            .toList();
    }

    /**
     * Build ICL prompt from retrieved experiences.
     */
    public String buildICLPrompt(String currentTask, List<Experience> experiences) {
        if (experiences.isEmpty()) {
            return "Current task:\n" + currentTask;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Relevant historical experiences:\n\n");

        for (int i = 0; i < experiences.size(); i++) {
            Experience exp = experiences.get(i);
            sb.append(String.format("### Experience %d\n", i + 1));
            sb.append(String.format("**Task**: %s\n", exp.task()));
            sb.append(String.format("**Strategy**: %s\n", exp.strategy()));
            sb.append(String.format("**Outcome**: %s\n", exp.outcome()));
            sb.append(String.format("**Quality**: %.2f\n\n", exp.qualityScore()));
        }

        sb.append("---\n\n");
        sb.append("Current task:\n");
        sb.append(currentTask);

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
