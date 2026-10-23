package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.common.LogHelper;
import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.event.MemoryRefineEventPublisher;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SummaryRepository;
import com.ablueforce.cortexce.util.XmlParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Generates and durably completes SessionEnd summaries. */
@Service
public class SummaryGenerationService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(SummaryGenerationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final SessionManagementService sessionManagementService;
    private final ObservationRepository observationRepository;
    private final PendingMessageRepository pendingMessageRepository;
    private final SummaryRepository summaryRepository;
    private final SSEBroadcaster sseBroadcaster;
    private final LlmService llmService;
    private final TemplateService templateService;
    private final ContextCacheService contextCacheService;
    private final MemoryRefineEventPublisher eventPublisher;
    private final QualityScorer qualityScorer;
    private final SummaryJobService summaryJobService;
    private final SummaryResultPersistenceService summaryResultPersistenceService;
    private final ConversationWriteService conversationWriteService;

    @Value("${claudemem.max-retries:3}")
    private int maxRetries;

    @Value("${claudemem.summary-job-stale-ms:900000}")
    private long summaryJobStaleMs;

    @Override
    public Logger getLogger() {
        return log;
    }

    public SummaryGenerationService(SessionManagementService sessionManagementService,
                                    ObservationRepository observationRepository,
                                    PendingMessageRepository pendingMessageRepository,
                                    SummaryRepository summaryRepository,
                                    SSEBroadcaster sseBroadcaster,
                                    LlmService llmService,
                                    TemplateService templateService,
                                    ContextCacheService contextCacheService,
                                    MemoryRefineEventPublisher eventPublisher,
                                    QualityScorer qualityScorer,
                                    SummaryJobService summaryJobService,
                                    SummaryResultPersistenceService summaryResultPersistenceService,
                                    ConversationWriteService conversationWriteService) {
        this.sessionManagementService = sessionManagementService;
        this.observationRepository = observationRepository;
        this.pendingMessageRepository = pendingMessageRepository;
        this.summaryRepository = summaryRepository;
        this.sseBroadcaster = sseBroadcaster;
        this.llmService = llmService;
        this.templateService = templateService;
        this.contextCacheService = contextCacheService;
        this.eventPublisher = eventPublisher;
        this.qualityScorer = qualityScorer;
        this.summaryJobService = summaryJobService;
        this.summaryResultPersistenceService = summaryResultPersistenceService;
        this.conversationWriteService = conversationWriteService;
    }

    /**
     * Process one durable SessionEnd job. The LLM call is deliberately outside every database
     * transaction; only short state transitions and result persistence hold the conversation
     * lock.
     */
    public void processSummaryJob(UUID jobId) {
        PendingMessageEntity job = summaryJobService.findJob(jobId).orElse(null);
        if (job == null || !SummaryJobService.TYPE_SUMMARIZE.equals(job.getMessageType())) {
            return;
        }

        UUID conversationId = job.getSessionDbId();
        try {
            if (job.getResultDeletedAt() != null && job.getResultSummaryId() == null) {
                return;
            }
            if (SummaryJobService.STATUS_PROCESSED.equals(job.getStatus())
                && job.getResultSummaryId() != null) {
                return;
            }
            if (job.getResultSummaryId() != null) {
                finishPersistedResult(job);
                return;
            }
            if (SummaryJobService.STATUS_PROCESSED.equals(job.getStatus())
                || SummaryJobService.STATUS_SKIPPED.equals(job.getStatus())
                || SummaryJobService.STATUS_FAILED.equals(job.getStatus())) {
                return;
            }

            long staleBefore = System.currentTimeMillis() - Math.max(1L, summaryJobStaleMs);
            Optional<PendingMessageEntity> claimed = summaryJobService
                .claimForProcessing(jobId, staleBefore);
            if (claimed.isEmpty()) {
                return;
            }
            job = claimed.get();

            if (job.getResultSummaryId() != null) {
                finishPersistedResult(job);
                return;
            }

            if (pendingMessageRepository.countOpenObservationJobs(conversationId) > 0) {
                summaryJobService.deferUntilObservationQueueDrains(jobId, conversationId);
                return;
            }

            SessionEntity session = sessionManagementService.findById(conversationId)
                .orElseThrow(() -> new SummaryJobService.ConversationGoneException(conversationId));
            List<ObservationEntity> observations = observationRepository
                .findByConversationIdOrderByCreatedAtEpochAsc(conversationId);
            if (observations.isEmpty()) {
                summaryJobService.markSkipped(jobId, conversationId, "no observations");
                return;
            }

            Map<String, Object> snapshot = job.getSummaryInputSnapshot();
            if (snapshot == null || snapshot.isEmpty()) {
                snapshot = buildInputSnapshot(session, observations);
                job = summaryJobService.persistSnapshot(
                    jobId, conversationId, snapshot, canonicalJson(snapshot));
            } else {
                job = summaryJobService.persistSnapshot(
                    jobId, conversationId, snapshot, canonicalJson(snapshot));
            }
            snapshot = job.getSummaryInputSnapshot();
            if (snapshot == null || snapshot.isEmpty()) {
                throw new IllegalStateException("summary job has no persisted input snapshot: " + jobId);
            }

            String systemPrompt = requiredSnapshotString(snapshot, "system_prompt");
            String userPrompt = requiredSnapshotString(snapshot, "user_prompt");
            logDataIn("Generating summary for conversation {} ({} observations)",
                conversationId, observations.size());

            String llmResponse = llmService.chatCompletion(systemPrompt, userPrompt);
            XmlParser.ParsedSummary parsed = XmlParser.parseSummary(llmResponse);
            int lastPromptNumber = lastPromptNumber(observations);

            SummaryEntity saved = persistSummaryResult(
                jobId, conversationId, session, parsed, lastPromptNumber);
            contextCacheService.markForRefresh(session.getProjectPath());
            triggerQualityScoringAndRefinement(
                session, observations, parsed, session.getLastAssistantMessage());
            summaryJobService.finalizeResult(jobId, conversationId, true);
            eventPublisher.publishRefineEvent(session.getProjectPath(), session.getContentSessionId());
            logSuccess("Summary saved for conversation {} as {}", conversationId, saved.getId());
        } catch (Exception e) {
            log.error("Failed to process summary job {} for conversation {}: {}",
                jobId, conversationId, e.getClass().getSimpleName());
            try {
                summaryJobService.markFailed(jobId, conversationId, maxRetries,
                    e.getClass().getSimpleName());
            } catch (SummaryJobService.ConversationGoneException gone) {
                log.debug("Summary job {} disappeared with conversation {}", jobId, conversationId);
            } catch (Exception stateFailure) {
                log.error("Failed to persist failure for summary job {}", jobId, stateFailure);
            }
        }
    }

    /** Finish a result whose Summary row and job pointer survived a prior crash. */
    private void finishPersistedResult(PendingMessageEntity job) {
        if (SummaryJobService.STATUS_PROCESSED.equals(job.getStatus())) {
            return;
        }
        UUID conversationId = job.getSessionDbId();
        SummaryEntity summary = summaryRepository.findById(job.getResultSummaryId())
            .orElseThrow(() -> new IllegalStateException(
                "summary job points to missing result: " + job.getId()));
        SessionEntity session = sessionManagementService.findById(conversationId)
            .orElseThrow(() -> new SummaryJobService.ConversationGoneException(conversationId));
        List<ObservationEntity> observations = observationRepository
            .findByConversationIdOrderByCreatedAtEpochAsc(conversationId);
        XmlParser.ParsedSummary parsed = parsedSummary(summary);
        contextCacheService.markForRefresh(session.getProjectPath());
        triggerQualityScoringAndRefinement(
            session, observations, parsed, session.getLastAssistantMessage());
        summaryJobService.finalizeResult(job.getId(), conversationId, true);
        eventPublisher.publishRefineEvent(session.getProjectPath(), session.getContentSessionId());
    }

    /** Persist the Summary row and its durable result pointer in one short transaction. */
    public SummaryEntity persistSummaryResult(UUID jobId, UUID conversationId,
                                              SessionEntity session,
                                              XmlParser.ParsedSummary parsed,
                                              Integer promptNumber) {
        SummaryEntity candidate = createSummary(
            session, parsed, promptNumber, SummaryJobService.JOB_KEY_SESSION_SUMMARY);
        return summaryResultPersistenceService.persist(jobId, conversationId, candidate);
    }

    /** Save a summary directly (from parsed XML or API input). */
    @Transactional
    public SummaryEntity saveSummary(UUID conversationId, String projectPath,
                                     XmlParser.ParsedSummary parsed, Integer promptNumber) {
        SessionEntity parent = sessionManagementService.findById(conversationId)
            .orElseThrow(() -> new IllegalStateException(
                "cannot save summary: conversation " + conversationId + " does not exist"));
        SummaryEntity summary = createSummary(parent, parsed, promptNumber, null);
        summary.setProjectPath(projectPath);
        SummaryEntity saved = summaryRepository.save(summary);
        publishSummaryAfterCommit(saved);
        return saved;
    }

    private SummaryEntity createSummary(SessionEntity session, XmlParser.ParsedSummary parsed,
                                        Integer promptNumber, String jobKey) {
        SummaryEntity summary = new SummaryEntity();
        summary.setConversationId(session.getId());
        summary.setContentSessionId(session.getContentSessionId());
        summary.setPlatformSource(session.getPlatformSource());
        summary.setProjectPath(session.getProjectPath());
        summary.setRequest(parsed.request);
        summary.setInvestigated(parsed.investigated);
        summary.setLearned(parsed.learned);
        summary.setCompleted(parsed.completed);
        summary.setNextSteps(parsed.nextSteps);
        summary.setNotes(parsed.notes);
        summary.setPromptNumber(promptNumber);
        summary.setCreatedAtEpoch(Instant.now().toEpochMilli());
        summary.setSummaryJobKey(jobKey);
        return summary;
    }

    private Map<String, Object> buildInputSnapshot(SessionEntity session,
                                                    List<ObservationEntity> observations) {
        String digest = buildObservationDigest(observations);
        String userPrompt = templateService.getSummaryPromptTemplate()
            .replace("{{lastAssistantMessage}}",
                session.getLastAssistantMessage() != null
                    ? session.getLastAssistantMessage() : "(not available)");
        String systemPrompt = "You are a memory observer agent. Summarize the following session observations:\n\n"
            + digest;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", "session-summary-v1");
        snapshot.put("conversation_id", session.getId().toString());
        snapshot.put("observation_ids", observations.stream()
            .map(ObservationEntity::getId)
            .map(String::valueOf)
            .toList());
        snapshot.put("system_prompt", systemPrompt);
        snapshot.put("user_prompt", userPrompt);
        return snapshot;
    }

    private String canonicalJson(Map<String, Object> snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise summary input snapshot", e);
        }
    }

    private String requiredSnapshotString(Map<String, Object> snapshot, String key) {
        Object value = snapshot.get(key);
        if (!(value instanceof String text) || text.isEmpty()) {
            throw new IllegalStateException("summary input snapshot is missing " + key);
        }
        return text;
    }

    private int lastPromptNumber(List<ObservationEntity> observations) {
        ObservationEntity last = observations.get(observations.size() - 1);
        return last.getPromptNumber() == null ? 0 : last.getPromptNumber();
    }

    private XmlParser.ParsedSummary parsedSummary(SummaryEntity summary) {
        XmlParser.ParsedSummary parsed = new XmlParser.ParsedSummary();
        parsed.request = summary.getRequest();
        parsed.investigated = summary.getInvestigated();
        parsed.learned = summary.getLearned();
        parsed.completed = summary.getCompleted();
        parsed.nextSteps = summary.getNextSteps();
        parsed.notes = summary.getNotes();
        return parsed;
    }

    private String buildObservationDigest(List<ObservationEntity> observations) {
        StringBuilder digest = new StringBuilder();
        for (int i = 0; i < observations.size(); i++) {
            ObservationEntity obs = observations.get(i);
            digest.append(String.format("[%d] %s: %s", i + 1, obs.getType(), obs.getTitle()));
            if (obs.getSubtitle() != null) digest.append(" - ").append(obs.getSubtitle());
            digest.append("\n");
            if (obs.getContent() != null) digest.append("  ").append(obs.getContent()).append("\n");
            if (obs.getFacts() != null && !obs.getFacts().isEmpty()) {
                obs.getFacts().forEach(f -> digest.append("  * ").append(f).append("\n"));
            }
        }
        return digest.toString();
    }

    private void publishSummaryAfterCommit(SummaryEntity saved) {
        Runnable publish = () -> {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", "new_summary");
            eventData.put("summary", saved);
            sseBroadcaster.broadcast(eventData, "new_summary");
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private void triggerQualityScoringAndRefinement(SessionEntity session,
                                                     List<ObservationEntity> observations,
                                                     XmlParser.ParsedSummary parsed,
                                                     String lastAssistantMessage) {
        try {
            QualityScorer.FeedbackType feedback = null;
            if (qualityScorer.isLlmAvailable()) {
                String sessionSummary = (parsed.completed != null ? parsed.completed : "")
                    + " " + (parsed.learned != null ? parsed.learned : "");
                feedback = qualityScorer.inferFeedbackWithLlm(
                    sessionSummary, lastAssistantMessage, observations.size());
            }
            if (feedback == null) {
                Long completed = session.getCompletedAtEpoch();
                Long started = session.getStartedAtEpoch();
                long duration = completed != null && started != null ? completed - started : 0;
                feedback = inferFeedback(lastAssistantMessage, observations.size(), duration);
            }

            OffsetDateTime now = OffsetDateTime.now();
            for (ObservationEntity obs : observations) {
                obs.setFeedbackType(feedback.name().toLowerCase());
                float quality = qualityScorer.isLlmAvailable()
                    ? qualityScorer.estimateQualityWithLlm(
                        obs.getTitle(), obs.getType(), obs.getContent(),
                        obs.getFacts() != null ? obs.getFacts().toString() : null)
                    : qualityScorer.estimateQuality(
                        feedback, obs.getContent(), null, observations.size());
                obs.setQualityScore(quality);
                obs.setFeedbackUpdatedAt(now);
            }
            conversationWriteService.saveSummaryQualityFeedback(session.getId(), observations);
        } catch (Exception e) {
            log.warn("Failed to persist summary quality metadata: {}", e.getClass().getSimpleName());
        }
    }

    private QualityScorer.FeedbackType inferFeedback(String lastAssistantMessage,
                                                     int observationCount,
                                                     long sessionDurationMs) {
        if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()) {
            String lower = lastAssistantMessage.toLowerCase();
            if (lower.contains("完成") || lower.contains("解决")
                || lower.contains("completed") || lower.contains("finished")
                || lower.contains("done") || lower.contains("solved")
                || lower.contains("已解决") || lower.contains("成功了")) {
                return QualityScorer.FeedbackType.SUCCESS;
            }
            if (lower.contains("无法") || lower.contains("失败")
                || lower.contains("failed") || lower.contains("cannot")
                || lower.contains("unable") || lower.contains("error")
                || lower.contains("无法完成")) {
                return QualityScorer.FeedbackType.FAILURE;
            }
        }
        if (observationCount < 3 || (sessionDurationMs < 5000 && observationCount < 5)) {
            return QualityScorer.FeedbackType.FAILURE;
        }
        return QualityScorer.FeedbackType.PARTIAL;
    }
}
