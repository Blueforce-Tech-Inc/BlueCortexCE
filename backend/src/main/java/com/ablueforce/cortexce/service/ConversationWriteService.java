package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.PendingMessageEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.entity.UserPromptEntity;
import com.ablueforce.cortexce.event.PendingMessageEventPublisher;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.PendingMessageRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.repository.UserPromptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/** Serializes Conversation-owned writes with SessionEnd and Viewer deletion. */
@Service
public class ConversationWriteService {

    private final SessionRepository sessionRepository;
    private final PendingMessageRepository pendingMessageRepository;
    private final ObservationRepository observationRepository;
    private final UserPromptRepository userPromptRepository;
    private final SSEBroadcaster sseBroadcaster;
    private final PendingMessageEventPublisher pendingMessageEventPublisher;

    public ConversationWriteService(SessionRepository sessionRepository,
                                    PendingMessageRepository pendingMessageRepository,
                                    ObservationRepository observationRepository,
                                    UserPromptRepository userPromptRepository,
                                    SSEBroadcaster sseBroadcaster,
                                    PendingMessageEventPublisher pendingMessageEventPublisher) {
        this.sessionRepository = sessionRepository;
        this.pendingMessageRepository = pendingMessageRepository;
        this.observationRepository = observationRepository;
        this.userPromptRepository = userPromptRepository;
        this.sseBroadcaster = sseBroadcaster;
        this.pendingMessageEventPublisher = pendingMessageEventPublisher;
    }

    @Transactional
    public UserPromptEntity savePrompt(UserPromptEntity prompt) {
        SessionEntity session = lockAndLoad(prompt.getConversationId());
        requireActive(session);
        requireOwnedIdentity(session, prompt.getContentSessionId(), prompt.getPlatformSource());
        return userPromptRepository.save(prompt);
    }

    /** Lock a Conversation while updating an existing child, import, or refinement result. */
    @Transactional
    public void lockForChildMutation(UUID conversationId) {
        if (conversationId == null) {
            return;
        }
        SessionEntity session = lockAndLoad(conversationId);
        requireChildMutationWindow(session);
    }

    /** Lock multiple parents in UUID order before a bulk child mutation. */
    @Transactional
    public void lockForChildMutations(Collection<UUID> conversationIds) {
        for (UUID conversationId : new TreeSet<>(conversationIds)) {
            SessionEntity session = lockAndLoad(conversationId);
            requireChildMutationWindow(session);
        }
    }

    @Transactional
    public boolean deleteLowQualityObservation(UUID observationId, UUID conversationId,
                                               String expectedProjectPath, String expectedType,
                                               String expectedSource, Float expectedQuality,
                                               float threshold) {
        if (conversationId != null && !lockForRefinement(conversationId)) {
            return false;
        }
        ObservationEntity current = observationRepository.findById(observationId).orElse(null);
        if (current == null || !Objects.equals(conversationId, current.getConversationId())
            || current.getStandaloneKind() != null
            || !Objects.equals(expectedProjectPath, current.getProjectPath())
            || !Objects.equals(expectedType, current.getType())
            || !Objects.equals(expectedSource, current.getSource())
            || !Objects.equals(expectedQuality, current.getQualityScore())
            || current.getQualityScore() == null || current.getQualityScore() >= threshold
            || current.getType() == null || current.getType().startsWith("extracted_")
            || "dlq".equals(current.getSource())) {
            return false;
        }
        return observationRepository.deleteByIdForViewer(observationId) != 0;
    }

    @Transactional
    public boolean persistRefinedObservation(UUID observationId, UUID conversationId,
                                             String expectedTitle, String expectedContent,
                                             String rewrittenContent, OffsetDateTime refinedAt) {
        if (conversationId != null && !lockForRefinement(conversationId)) {
            return false;
        }
        ObservationEntity current = observationRepository.findById(observationId).orElse(null);
        if (current == null || !Objects.equals(conversationId, current.getConversationId())
            || current.getStandaloneKind() != null
            || !Objects.equals(expectedTitle, current.getTitle())
            || !Objects.equals(expectedContent, current.getContent())) {
            return false;
        }
        current.setContent(rewrittenContent);
        current.setRefinedAt(refinedAt);
        observationRepository.saveAndFlush(current);
        return true;
    }

    @Transactional
    public boolean mergeRefinedObservations(UUID conversationId, List<ObservationEntity> snapshots,
                                            String mergedContent, OffsetDateTime refinedAt) {
        if (conversationId == null || snapshots.size() < 2 || !lockForRefinement(conversationId)) {
            return false;
        }
        List<UUID> ids = snapshots.stream().map(ObservationEntity::getId).toList();
        Map<UUID, ObservationEntity> currentById = observationRepository.findAllById(ids).stream()
            .collect(java.util.stream.Collectors.toMap(ObservationEntity::getId, observation -> observation));
        if (currentById.size() != snapshots.size()) {
            return false;
        }
        for (ObservationEntity snapshot : snapshots) {
            ObservationEntity current = currentById.get(snapshot.getId());
            if (current == null || !conversationId.equals(current.getConversationId())
                || current.getStandaloneKind() != null
                || !Objects.equals(snapshot.getTitle(), current.getTitle())
                || !Objects.equals(snapshot.getContent(), current.getContent())
                || !Objects.equals(snapshot.getFacts(), current.getFacts())
                || !Objects.equals(snapshot.getConcepts(), current.getConcepts())
                || !Objects.equals(snapshot.getFilesRead(), current.getFilesRead())
                || !Objects.equals(snapshot.getFilesModified(), current.getFilesModified())
                || !Objects.equals(snapshot.getQualityScore(), current.getQualityScore())
                || !Objects.equals(snapshot.getFeedbackType(), current.getFeedbackType())
                || !Objects.equals(snapshot.getUserComment(), current.getUserComment())) {
                return false;
            }
        }

        ObservationEntity primary = currentById.get(snapshots.get(0).getId());
        List<UUID> duplicates = ids.stream().filter(id -> !id.equals(primary.getId())).toList();
        observationRepository.deleteAllById(duplicates);
        primary.setContent(mergedContent);
        primary.setRefinedAt(refinedAt);
        observationRepository.saveAndFlush(primary);
        return true;
    }

    /** Persist only summary-derived quality metadata after the summary snapshot is fixed. */
    @Transactional
    public void saveSummaryQualityFeedback(UUID conversationId, List<ObservationEntity> scoredObservations) {
        if (sessionRepository.lockSessionForUpdate(conversationId).isEmpty()) {
            return;
        }
        List<ObservationEntity> current = observationRepository.findAllById(
            scoredObservations.stream().map(ObservationEntity::getId).toList());
        Map<UUID, ObservationEntity> currentById = current.stream()
            .collect(java.util.stream.Collectors.toMap(ObservationEntity::getId, observation -> observation));
        List<ObservationEntity> toSave = new java.util.ArrayList<>();
        for (ObservationEntity scored : scoredObservations) {
            ObservationEntity target = currentById.get(scored.getId());
            if (target == null || !conversationId.equals(target.getConversationId())) {
                continue;
            }
            target.setFeedbackType(scored.getFeedbackType());
            target.setQualityScore(scored.getQualityScore());
            target.setFeedbackUpdatedAt(scored.getFeedbackUpdatedAt());
            toSave.add(target);
        }
        if (!toSave.isEmpty()) {
            observationRepository.saveAll(toSave);
        }
    }

    /** Persist the prepared Observation and complete its admitted queue row atomically. */
    @Transactional
    public ObservationEntity saveObservation(UUID conversationId, UUID pendingId,
                                            ObservationEntity observation,
                                            long duplicateWindowStartEpoch) {
        SessionEntity session = lockAndLoad(conversationId);
        requireOwnedIdentity(session, observation.getContentSessionId(), observation.getPlatformSource());
        if (!conversationId.equals(observation.getConversationId())) {
            throw new IllegalArgumentException("observation conversation does not match its parent");
        }

        PendingMessageEntity pending = pendingId == null
            ? null : requireProcessingObservation(pendingId, conversationId);
        if (pending == null) {
            requireActive(session);
        } else if (!"active".equals(session.getStatus()) && !"summarizing".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Conversation no longer accepts admitted Observation work");
        }

        Optional<ObservationEntity> duplicate = observationRepository.findDuplicateByContentHash(
            observation.getContentHash(), duplicateWindowStartEpoch, observation.getMemoryScopeId());
        ObservationEntity saved = duplicate.orElseGet(() -> observationRepository.saveAndFlush(observation));

        if (pending != null) {
            pending.setStatus("processed");
            pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
            pending.setStartedProcessingAtEpoch(null);
            pendingMessageRepository.saveAndFlush(pending);
            wakeSummaryIfDrained(session);
        }
        if (duplicate.isEmpty()) {
            broadcastAfterCommit(saved);
        }
        return saved;
    }

    @Transactional
    public Optional<PendingMessageEntity> claimObservationJob(UUID pendingId) {
        PendingMessageEntity current = pendingMessageRepository.findById(pendingId).orElse(null);
        if (current == null || !"observation".equals(current.getMessageType())) {
            return Optional.empty();
        }
        SessionEntity session = lockAndLoad(current.getSessionDbId());
        PendingMessageEntity pending = pendingMessageRepository.findById(pendingId).orElse(null);
        if (pending == null || !"observation".equals(pending.getMessageType())
            || !"pending".equals(pending.getStatus())) {
            return Optional.empty();
        }
        if (!"active".equals(session.getStatus()) && !"summarizing".equals(session.getStatus())) {
            pending.setStatus("failed");
            pending.setFailedAtEpoch(Instant.now().toEpochMilli());
            pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
            pendingMessageRepository.save(pending);
            wakeSummaryIfDrained(session);
            return Optional.empty();
        }
        pending.setStatus("processing");
        pending.setStartedProcessingAtEpoch(Instant.now().toEpochMilli());
        return Optional.of(pendingMessageRepository.saveAndFlush(pending));
    }

    @Transactional
    public void markObservationSkipped(UUID pendingId, UUID conversationId) {
        SessionEntity session = lockAndLoad(conversationId);
        PendingMessageEntity pending = requireProcessingObservation(pendingId, conversationId);
        pending.setStatus("skipped");
        pending.setCompletedAtEpoch(Instant.now().toEpochMilli());
        pending.setStartedProcessingAtEpoch(null);
        pendingMessageRepository.saveAndFlush(pending);
        wakeSummaryIfDrained(session);
    }

    @Transactional
    public void markObservationFailed(UUID pendingId, int maxRetries, long nowEpoch) {
        PendingMessageEntity current = pendingMessageRepository.findById(pendingId).orElse(null);
        if (current == null || !"observation".equals(current.getMessageType())) {
            return;
        }
        SessionEntity session = lockAndLoad(current.getSessionDbId());
        PendingMessageEntity pending = pendingMessageRepository.findById(pendingId).orElse(null);
        if (pending == null || !"processing".equals(pending.getStatus())) {
            return;
        }
        int retryCount = (pending.getRetryCount() == null ? 0 : pending.getRetryCount()) + 1;
        pending.setRetryCount(retryCount);
        pending.setStartedProcessingAtEpoch(null);
        if (retryCount < Math.max(1, maxRetries)) {
            pending.setStatus("pending");
            pending.setFailedAtEpoch(null);
        } else {
            pending.setStatus("failed");
            pending.setFailedAtEpoch(nowEpoch);
            pending.setCompletedAtEpoch(nowEpoch);
        }
        pendingMessageRepository.saveAndFlush(pending);
        if ("failed".equals(pending.getStatus())) {
            wakeSummaryIfDrained(session);
        }
    }

    private PendingMessageEntity requireProcessingObservation(UUID pendingId, UUID conversationId) {
        PendingMessageEntity pending = pendingMessageRepository.findById(pendingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "admitted Observation job no longer exists"));
        if (!conversationId.equals(pending.getSessionDbId())
            || !"observation".equals(pending.getMessageType())
            || !"processing".equals(pending.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "admitted Observation job is not in a writable state");
        }
        return pending;
    }

    private SessionEntity lockAndLoad(UUID conversationId) {
        if (conversationId == null || sessionRepository.lockSessionForUpdate(conversationId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return sessionRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private void requireActive(SessionEntity session) {
        if (!"active".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Conversation no longer accepts new writes");
        }
    }

    private void requireChildMutationWindow(SessionEntity session) {
        if ("summarizing".equals(session.getStatus())
            || pendingMessageRepository.existsInFlightSummaryJob(
                session.getId(), SummaryJobService.IN_FLIGHT_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Conversation has an in-flight SessionEnd summary");
        }
    }

    private boolean lockForRefinement(UUID conversationId) {
        if (sessionRepository.lockSessionForUpdate(conversationId).isEmpty()) {
            return false;
        }
        SessionEntity session = sessionRepository.findById(conversationId).orElse(null);
        return session != null
            && !"summarizing".equals(session.getStatus())
            && !pendingMessageRepository.existsInFlightSummaryJob(
                conversationId, SummaryJobService.IN_FLIGHT_STATUSES);
    }

    private void requireOwnedIdentity(SessionEntity session, String contentSessionId, String platformSource) {
        if (!Objects.equals(session.getContentSessionId(), contentSessionId)
            || !Objects.equals(session.getPlatformSource(), platformSource)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "child identity does not match its Conversation");
        }
    }

    private void wakeSummaryIfDrained(SessionEntity session) {
        if (!"summarizing".equals(session.getStatus())
            || pendingMessageRepository.countOpenObservationJobs(session.getId()) != 0) {
            return;
        }
        pendingMessageRepository.findSummaryJob(
                session.getId(), SummaryJobService.JOB_KEY_SESSION_SUMMARY)
            .filter(job -> SummaryJobService.STATUS_PENDING.equals(job.getStatus()))
            .ifPresent(job -> afterCommit(() -> pendingMessageEventPublisher.publishPendingMessageEvent(
                job.getId(), SummaryJobService.TYPE_SUMMARIZE)));
    }

    private void broadcastAfterCommit(ObservationEntity saved) {
        afterCommit(() -> sseBroadcaster.broadcast(
            java.util.Map.of("type", "new_observation", "observation", saved), "new_observation"));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
