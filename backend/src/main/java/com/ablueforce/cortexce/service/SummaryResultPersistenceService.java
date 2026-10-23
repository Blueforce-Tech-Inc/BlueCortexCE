package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SummaryEntity;
import com.ablueforce.cortexce.repository.SummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/** Persists a SessionEnd summary and its durable job pointer in one transaction. */
@Service
public class SummaryResultPersistenceService {

    private final SummaryRepository summaryRepository;
    private final SummaryJobService summaryJobService;
    private final SSEBroadcaster sseBroadcaster;

    public SummaryResultPersistenceService(SummaryRepository summaryRepository,
                                           SummaryJobService summaryJobService,
                                           SSEBroadcaster sseBroadcaster) {
        this.summaryRepository = summaryRepository;
        this.summaryJobService = summaryJobService;
        this.sseBroadcaster = sseBroadcaster;
    }

    @Transactional
    public SummaryEntity persist(UUID jobId, UUID conversationId, SummaryEntity candidate) {
        Optional<SummaryEntity> existing = summaryRepository
            .findByConversationIdAndSummaryJobKey(
                conversationId, SummaryJobService.JOB_KEY_SESSION_SUMMARY);
        SummaryEntity saved;
        boolean created = false;
        if (existing.isPresent()) {
            saved = existing.get();
        } else {
            saved = summaryRepository.saveAndFlush(candidate);
            created = true;
        }

        summaryJobService.markResultPersisted(jobId, conversationId, saved.getId());
        if (created) {
            publishAfterCommit(saved);
        }
        return saved;
    }

    private void publishAfterCommit(SummaryEntity saved) {
        Runnable publish = () -> {
            HashMap<String, Object> event = new HashMap<>();
            event.put("type", "new_summary");
            event.put("summary", saved);
            sseBroadcaster.broadcast(event, "new_summary");
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
}
