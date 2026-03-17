package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for memory refinement - pruning, merging, and rewriting observations.
 * 
 * Based on Evo-Memory paper Section 6.2.1 - Refine Memory Mechanism.
 * 
 * Architecture: Asynchronous refinement triggered at SessionEnd.
 * Effect: Delayed - visible in next session.
 */
@Service
public class MemoryRefineService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRefineService.class);

    // Quality thresholds
    private static final float DELETE_THRESHOLD = 0.3f;
    private static final float STALE_QUALITY_THRESHOLD = 0.6f;
    
    // Time thresholds
    private static final int STALE_DAYS = 30;
    private static final int REFINED_COOLDOWN_DAYS = 7;
    
    // Batch sizes
    private static final int REFINE_BATCH_SIZE = 20;

    private final ObservationRepository observationRepository;
    private final QualityScorer qualityScorer;
    private final LlmService llmService;

    public MemoryRefineService(ObservationRepository observationRepository,
                             QualityScorer qualityScorer,
                             LlmService llmService) {
        this.observationRepository = observationRepository;
        this.qualityScorer = qualityScorer;
        this.llmService = llmService;
    }

    /**
     * Trigger memory refinement for a project.
     * Called at SessionEnd or by scheduled task.
     * 
     * @param projectPath Project path to refine
     */
    @Async
    public void refineMemory(String projectPath) {
        log.info("Starting memory refinement for project: {}", projectPath);
        
        try {
            // Step 1: Find candidates
            List<ObservationEntity> candidates = findRefineCandidates(projectPath);
            
            if (candidates.isEmpty()) {
                log.debug("No candidates found for refinement in project: {}", projectPath);
                return;
            }
            
            log.info("Found {} candidates for refinement", candidates.size());
            
            // Step 2: Categorize candidates
            List<ObservationEntity> toDelete = candidates.stream()
                .filter(o -> o.getQualityScore() != null && o.getQualityScore() < DELETE_THRESHOLD)
                .collect(Collectors.toList());
            
            List<ObservationEntity> toRefine = candidates.stream()
                .filter(o -> !toDelete.contains(o))
                .limit(REFINE_BATCH_SIZE)
                .collect(Collectors.toList());
            
            // Step 3: Execute deletion
            if (!toDelete.isEmpty()) {
                deleteLowQualityObservations(toDelete);
            }
            
            // Step 4: Execute refinement (merge/rewrite)
            if (!toRefine.isEmpty()) {
                refineObservations(toRefine, projectPath);
            }
            
            log.info("Memory refinement completed for project: {}", projectPath);
            
        } catch (Exception e) {
            log.error("Memory refinement failed for project: {}", projectPath, e);
        }
    }

    /**
     * Quick refinement - called by frequent scheduled task.
     * Lightweight, processes fewer observations.
     */
    @Async
    public void quickRefine(String projectPath, int maxCount) {
        log.debug("Starting quick refinement for project: {}", projectPath);
        
        try {
            // Just process low-quality observations (no LLM call)
            List<ObservationEntity> lowQuality = observationRepository
                .findLowQualityObservations(projectPath, DELETE_THRESHOLD, maxCount);
            
            if (!lowQuality.isEmpty()) {
                deleteLowQualityObservations(lowQuality);
                log.info("Quick refinement: deleted {} low-quality observations", lowQuality.size());
            }
            
        } catch (Exception e) {
            log.error("Quick refinement failed for project: {}", projectPath, e);
        }
    }

    /**
     * Deep refinement - called by daily scheduled task.
     * Includes cross-session merging and rule extraction.
     */
    @Async
    public void deepRefineProjectMemories(String projectPath) {
        log.info("Starting deep refinement for project: {}", projectPath);
        
        try {
            // Step 1: Find all stale observations
            List<ObservationEntity> stale = observationRepository.findStaleObservations(
                projectPath,
                OffsetDateTime.now().minusDays(STALE_DAYS),
                STALE_QUALITY_THRESHOLD,
                50
            );
            
            // Step 2: Find overdue observations
            List<ObservationEntity> overdue = observationRepository.findOverdueForRefine(
                projectPath,
                OffsetDateTime.now().minusDays(REFINED_COOLDOWN_DAYS),
                30
            );
            
            // Step 3: Combine and process
            List<ObservationEntity> candidates = new java.util.ArrayList<>();
            candidates.addAll(stale);
            candidates.addAll(overdue);
            candidates = candidates.stream().distinct().limit(50).collect(Collectors.toList());
            
            if (!candidates.isEmpty()) {
                refineObservations(candidates, projectPath);
            }
            
            log.info("Deep refinement completed for project: {}", projectPath);
            
        } catch (Exception e) {
            log.error("Deep refinement failed for project: {}", projectPath, e);
        }
    }

    /**
     * Find candidates for refinement.
     * Multi-dimensional filtering.
     */
    private List<ObservationEntity> findRefineCandidates(String projectPath) {
        List<ObservationEntity> candidates = new java.util.ArrayList<>();
        
        // 1. Low quality candidates
        candidates.addAll(observationRepository.findLowQualityObservations(
            projectPath, DELETE_THRESHOLD, REFINE_BATCH_SIZE));
        
        // 2. Stale candidates
        candidates.addAll(observationRepository.findStaleObservations(
            projectPath,
            OffsetDateTime.now().minusDays(STALE_DAYS),
            STALE_QUALITY_THRESHOLD,
            REFINE_BATCH_SIZE));
        
        // 3. Overdue candidates (not refined in 7 days)
        candidates.addAll(observationRepository.findOverdueForRefine(
            projectPath,
            OffsetDateTime.now().minusDays(REFINED_COOLDOWN_DAYS),
            REFINE_BATCH_SIZE));
        
        // Filter out recently refined
        return candidates.stream()
            .filter(this::canRefine)
            .distinct()
            .limit(REFINE_BATCH_SIZE)
            .collect(Collectors.toList());
    }

    /**
     * Check if observation can be refined (not in cooldown period).
     */
    private boolean canRefine(ObservationEntity obs) {
        if (obs.getRefinedAt() == null) {
            return true; // Never refined
        }
        // Allow re-refinement after 7 days
        return obs.getRefinedAt().isBefore(OffsetDateTime.now().minusDays(REFINED_COOLDOWN_DAYS));
    }

    /**
     * Delete low-quality observations.
     */
    private void deleteLowQualityObservations(List<ObservationEntity> observations) {
        List<java.util.UUID> ids = observations.stream()
            .map(ObservationEntity::getId)
            .collect(Collectors.toList());
        
        observationRepository.deleteAllById(ids);
        log.info("Deleted {} low-quality observations", ids.size());
    }

    /**
     * Refine observations (merge similar, rewrite for better retrieval).
     * Uses LLM for intelligent decisions.
     */
    private void refineObservations(List<ObservationEntity> observations, String projectPath) {
        // Group by session for potential merging
        var bySession = observations.stream()
            .collect(Collectors.groupingBy(ObservationEntity::getMemorySessionId));
        
        for (var entry : bySession.entrySet()) {
            List<ObservationEntity> sessionObs = entry.getValue();
            
            if (sessionObs.size() > 1) {
                // Merge observations from same session
                mergeObservations(sessionObs);
            } else {
                // Rewrite single observation
                rewriteObservation(sessionObs.get(0));
            }
        }
        
        // Mark as refined
        OffsetDateTime now = OffsetDateTime.now();
        for (ObservationEntity obs : observations) {
            obs.setRefinedAt(now);
        }
        observationRepository.saveAll(observations);
        
        log.info("Refined {} observations", observations.size());
    }

    /**
     * Merge multiple observations into one.
     */
    private void mergeObservations(List<ObservationEntity> observations) {
        if (observations.size() < 2) return;
        
        // Build merge prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("Merge the following observations into a single consolidated observation:\n\n");
        
        for (int i = 0; i < observations.size(); i++) {
            ObservationEntity obs = observations.get(i);
            prompt.append(String.format("[%d] %s\n%s\n\n", 
                i + 1, 
                obs.getTitle() != null ? obs.getTitle() : "Untitled",
                obs.getContent() != null ? obs.getContent() : ""));
        }
        
        prompt.append("Provide a consolidated summary that captures the key information from all observations.");
        
        // Call LLM (simplified - actual implementation would use LlmService)
        // For now, just mark as refined
        log.debug("Would merge {} observations via LLM", observations.size());
    }

    /**
     * Rewrite single observation for better quality.
     */
    private void rewriteObservation(ObservationEntity observation) {
        // Simplified - actual implementation would use LLM to improve content
        log.debug("Would rewrite observation: {}", observation.getId());
    }
}
