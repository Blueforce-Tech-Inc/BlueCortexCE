package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.common.LogHelper;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.service.QualityScorer;
import com.ablueforce.cortexce.util.XmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Summary generation service.
 * <p>
 * Responsibilities:
 * - Generate session summaries from observations
 * - Persist summaries to database
 * - Broadcast SSE events
 * - Trigger quality scoring and memory refinement
 *
 * @author Cortex CE Team
 */
@Service
public class SummaryGenerationService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(SummaryGenerationService.class);

    @Override
    public Logger getLogger() {
        return log;
    }

    private final SessionManagementService sessionManagementService;
    private final ObservationRepository observationRepository;
    private final SummaryRepository summaryRepository;
    private final SSEBroadcaster sseBroadcaster;
    private final LlmService llmService;
    private final TemplateService templateService;
    private final ContextCacheService contextCacheService;
    private final MemoryRefineEventPublisher eventPublisher;
    private final QualityScorer qualityScorer;

    public SummaryGenerationService(SessionManagementService sessionManagementService,
                                   ObservationRepository observationRepository,
                                   SummaryRepository summaryRepository,
                                   SSEBroadcaster sseBroadcaster,
                                   LlmService llmService,
                                   TemplateService templateService,
                                   ContextCacheService contextCacheService,
                                   MemoryRefineEventPublisher eventPublisher,
                                   QualityScorer qualityScorer) {
        this.sessionManagementService = sessionManagementService;
        this.observationRepository = observationRepository;
        this.summaryRepository = summaryRepository;
        this.sseBroadcaster = sseBroadcaster;
        this.llmService = llmService;
        this.templateService = templateService;
        this.contextCacheService = contextCacheService;
        this.eventPublisher = eventPublisher;
        this.qualityScorer = qualityScorer;
    }

    /**
     * Complete a session and asynchronously generate a summary.
     * Gathers all observations for the session, calls LLM to produce a summary,
     * parses the XML, and persists the SummaryEntity.
     *
     * @param contentSessionId       The session ID from Claude Code
     * @param lastAssistantMessage   The last assistant message (from transcript)
     */
    @Async
    public void completeSessionAsync(String contentSessionId, String lastAssistantMessage) {
        try {
            Optional<SessionEntity> sessionOpt = sessionManagementService.completeSessionForSummary(
                contentSessionId, lastAssistantMessage);
            if (sessionOpt.isEmpty()) {
                logFailure("Cannot summarize: session not found for {}", contentSessionId);
                return;
            }
            SessionEntity session = sessionOpt.get();

            List<ObservationEntity> observations = observationRepository
                .findByContentSessionIdOrderByCreatedAtEpochAsc(contentSessionId);

            if (observations.isEmpty()) {
                logDataIn("No observations to summarize for session {}", contentSessionId);
                return;
            }

            // Build digest for LLM
            StringBuilder digest = buildObservationDigest(observations);

            String lastAssistantMsg = session.getLastAssistantMessage();
            String userPromptForSummary = templateService.getSummaryPromptTemplate()
                .replace("{{lastAssistantMessage}}", lastAssistantMsg != null ? lastAssistantMsg : "(not available)");
            String systemPrompt = "You are a memory observer agent. Summarize the following session observations:\n\n" + digest;

            logDataIn("Generating summary for session {} ({} observations)", contentSessionId, observations.size());
            String llmResponse = llmService.chatCompletion(systemPrompt, userPromptForSummary);

            XmlParser.ParsedSummary parsed = XmlParser.parseSummary(llmResponse);
            int lastPromptNumber = observations.get(observations.size() - 1).getPromptNumber() != null
                ? observations.get(observations.size() - 1).getPromptNumber() : 0;
            saveSummary(contentSessionId, session.getProjectPath(), parsed, lastPromptNumber);

            contextCacheService.markForRefresh(session.getProjectPath());

            logSuccess("Summary saved for session {}", contentSessionId);

            // Phase 2: Trigger quality scoring and memory refinement
            triggerQualityScoringAndRefinement(session, observations, parsed, lastAssistantMessage);

        } catch (Exception e) {
            logFailure("Failed to generate summary for session {}", contentSessionId, e);
        }
    }

    /**
     * Save a summary directly (from parsed XML or API input).
     */
    public SummaryEntity saveSummary(String contentSessionId, String projectPath,
                                      XmlParser.ParsedSummary parsed, Integer promptNumber) {
        SummaryEntity summary = new SummaryEntity();
        summary.setContentSessionId(contentSessionId);
        summary.setProjectPath(projectPath);
        summary.setRequest(parsed.request);
        summary.setInvestigated(parsed.investigated);
        summary.setLearned(parsed.learned);
        summary.setCompleted(parsed.completed);
        summary.setNextSteps(parsed.nextSteps);
        summary.setNotes(parsed.notes);
        summary.setPromptNumber(promptNumber);
        summary.setCreatedAtEpoch(Instant.now().toEpochMilli());

        SummaryEntity saved = summaryRepository.save(summary);

        // Broadcast SSE event with type field
        Map<String, Object> eventData = new java.util.HashMap<>();
        eventData.put("type", "new_summary");
        eventData.put("summary", saved);
        sseBroadcaster.broadcast(eventData, "new_summary");

        return saved;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private StringBuilder buildObservationDigest(List<ObservationEntity> observations) {
        StringBuilder digest = new StringBuilder();
        for (int i = 0; i < observations.size(); i++) {
            ObservationEntity obs = observations.get(i);
            digest.append(String.format("[%d] %s: %s",
                i + 1, obs.getType(), obs.getTitle()));
            if (obs.getSubtitle() != null) digest.append(" — ").append(obs.getSubtitle());
            digest.append("\n");
            if (obs.getContent() != null) digest.append("  ").append(obs.getContent()).append("\n");
            if (obs.getFacts() != null && !obs.getFacts().isEmpty()) {
                obs.getFacts().forEach(f -> digest.append("  • ").append(f).append("\n"));
            }
        }
        return digest;
    }

    private void triggerQualityScoringAndRefinement(SessionEntity session,
                                                      List<ObservationEntity> observations,
                                                      XmlParser.ParsedSummary parsed,
                                                      String lastAssistantMessage) {
        try {
            // Infer feedback type - prefer LLM-based inference
            QualityScorer.FeedbackType feedback = null;

            // Try LLM-based inference first (more accurate)
            if (qualityScorer.isLlmAvailable()) {
                // Build summary from parsed content (aligns with original AgentService)
                String sessionSummary = (parsed.completed != null ? parsed.completed : "")
                    + " " + (parsed.learned != null ? parsed.learned : "");
                feedback = qualityScorer.inferFeedbackWithLlm(
                    sessionSummary,
                    lastAssistantMessage,
                    observations.size()
                );
                log.debug("Using LLM-based feedback inference: {}", feedback);
            }

            // Fall back to rule-based if LLM failed or not available
            if (feedback == null) {
                Long completed = session.getCompletedAtEpoch();
                Long started = session.getStartedAtEpoch();
                long sessionDurationMs = (completed != null && started != null) ? completed - started : 0;
                feedback = inferFeedback(lastAssistantMessage, observations.size(), sessionDurationMs);
                log.debug("Using rule-based feedback inference: {}", feedback);
            }

            // Update quality scores for observations
            OffsetDateTime now = OffsetDateTime.now();
            for (ObservationEntity obs : observations) {
                obs.setFeedbackType(feedback.name().toLowerCase());

                // Prefer LLM-based quality scoring when available
                float quality;
                if (qualityScorer.isLlmAvailable()) {
                    quality = qualityScorer.estimateQualityWithLlm(
                        obs.getTitle(),
                        obs.getType(),
                        obs.getContent(),
                        obs.getFacts() != null ? obs.getFacts().toString() : null
                    );
                } else {
                    quality = qualityScorer.estimateQuality(
                        feedback,
                        obs.getContent(),
                        null,
                        observations.size()
                    );
                }

                obs.setQualityScore(quality);
                obs.setFeedbackUpdatedAt(now);
            }
            observationRepository.saveAll(observations);
            log.debug("Quality scores updated for {} observations", observations.size());

            // Trigger memory refinement via Spring Event (async processing)
            eventPublisher.publishRefineEvent(session.getProjectPath(), session.getContentSessionId());
            log.debug("Memory refinement event published for project {}", session.getProjectPath());

        } catch (Exception e) {
            log.warn("Failed to trigger quality scoring or refinement", e);
        }
    }

    /**
     * Infer feedback type from session information.
     * Based on Evo-Memory paper Section 6.1.2 - Feedback Inference.
     */
    private QualityScorer.FeedbackType inferFeedback(String lastAssistantMessage,
                                                      int observationCount,
                                                      long sessionDurationMs) {
        // 1. Parse success/failure signals from last message
        if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()) {
            String lower = lastAssistantMessage.toLowerCase();

            // Success signals
            if (lower.contains("完成") || lower.contains("解决") ||
                lower.contains("completed") || lower.contains("finished") ||
                lower.contains("done") || lower.contains("solved") ||
                lower.contains("已解决") || lower.contains("成功了")) {
                return QualityScorer.FeedbackType.SUCCESS;
            }

            // Failure signals
            if (lower.contains("无法") || lower.contains("失败") ||
                lower.contains("failed") || lower.contains("cannot") ||
                lower.contains("unable") || lower.contains("error") ||
                lower.contains("无法完成")) {
                return QualityScorer.FeedbackType.FAILURE;
            }
        }

        // 2. Heuristic based on observation count
        if (observationCount == 0) {
            return QualityScorer.FeedbackType.FAILURE;
        }
        if (observationCount < 3) {
            return QualityScorer.FeedbackType.FAILURE;
        }

        // 3. Session duration check
        if (sessionDurationMs < 5000 && observationCount < 5) {
            return QualityScorer.FeedbackType.FAILURE;
        }

        // Default to partial success
        return QualityScorer.FeedbackType.PARTIAL;
    }
}
