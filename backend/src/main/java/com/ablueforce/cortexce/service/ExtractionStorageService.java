package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.ObservationEntity;
import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.ObservationRepository;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.config.ExtractionConfig.TemplateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transactional helper for extraction result storage.
 * Extracted from StructuredExtractionService to ensure session find-or-create
 * and observation save are atomic within a single transaction.
 */
@Service
public class ExtractionStorageService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionStorageService.class);

    private final SessionRepository sessionRepository;
    private final ObservationRepository observationRepository;

    public ExtractionStorageService(SessionRepository sessionRepository,
                                     ObservationRepository observationRepository) {
        this.sessionRepository = sessionRepository;
        this.observationRepository = observationRepository;
    }

    /**
     * Store extraction result as a new ObservationEntity (append-only).
     * Transactional: session creation + observation save are atomic.
     */
    /**
     * Store extraction result as a new ObservationEntity (append-only).
     * Transactional: session creation + observation save are atomic.
     *
     * @throws IllegalArgumentException if sourceObservations is null or targetSessionId is blank
     */
    @Transactional
    public void storeExtractionResult(TemplateConfig template,
                                      Map<String, Object> result,
                                      List<ObservationEntity> sourceObservations,
                                      String targetSessionId,
                                      String projectPath) {
        if (result == null || result.isEmpty()) {
            log.warn("Empty extraction result for template '{}', skipping storage", template.getName());
            return;
        }
        if (sourceObservations == null) {
            throw new IllegalArgumentException("sourceObservations must not be null for template: " + template.getName());
        }
        if (targetSessionId == null || targetSessionId.isBlank()) {
            throw new IllegalArgumentException("targetSessionId must not be blank for template: " + template.getName());
        }

        // Ensure target session exists (FK constraint: mem_observations.content_session_id → mem_sessions)
        sessionRepository.findByContentSessionId(targetSessionId)
            .orElseGet(() -> {
                log.info("Creating extraction session: {}", targetSessionId);
                SessionEntity session = new SessionEntity();
                session.setContentSessionId(targetSessionId);
                session.setProjectPath(projectPath);
                session.setStatus("extraction");
                session.setStartedAtEpoch(System.currentTimeMillis());
                return sessionRepository.save(session);
            });

        ObservationEntity extraction = new ObservationEntity();
        extraction.setContentSessionId(targetSessionId);
        extraction.setProjectPath(projectPath);
        extraction.setType("extracted_" + template.getName());
        extraction.setTitle("Extraction: " + template.getName());
        extraction.setContent("Structured extraction result for template: " + template.getName());
        extraction.setSource("llm_extraction");
        extraction.setExtractedData(result);
        extraction.setCreatedAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
        extraction.setCreatedAtEpoch(System.currentTimeMillis());
        extraction.setPromptNumber(0);
        extraction.setConcepts(List.of("extraction", template.getName()));

        // Link to source observations
        String sourceIds = sourceObservations.stream()
            .map(o -> o.getId().toString())
            .collect(Collectors.joining(","));
        extraction.setRefinedFromIds(sourceIds);

        observationRepository.save(extraction);
        log.info("Stored extraction result for template '{}' in session '{}' ({} source observations)",
            template.getName(), targetSessionId, sourceObservations.size());
    }

    /**
     * Store extraction failure in DLQ (dead letter queue).
     * Transactional: session creation + DLQ observation save are atomic.
     */
    @Transactional
    public void storeDLQ(String projectPath, String templateName, String errorMsg) {
        try {
            String dlqSessionId = "dlq:extraction";
            sessionRepository.findByContentSessionId(dlqSessionId)
                .orElseGet(() -> {
                    log.info("Creating DLQ session: {}", dlqSessionId);
                    SessionEntity session = new SessionEntity();
                    session.setContentSessionId(dlqSessionId);
                    session.setProjectPath(projectPath);
                    session.setStatus("dlq");
                    session.setStartedAtEpoch(System.currentTimeMillis());
                    return sessionRepository.save(session);
                });

            ObservationEntity dlq = new ObservationEntity();
            dlq.setContentSessionId(dlqSessionId);
            dlq.setProjectPath(projectPath);
            dlq.setType("dlq_" + templateName);
            dlq.setTitle("DLQ: " + templateName);
            dlq.setContent("Extraction failed for template: " + templateName);
            dlq.setSource("dlq");
            dlq.setExtractedData(Map.of("error", errorMsg, "template", templateName));
            dlq.setCreatedAt(Instant.now().atOffset(java.time.ZoneOffset.UTC));
            dlq.setCreatedAtEpoch(System.currentTimeMillis());
            dlq.setPromptNumber(0);
            dlq.setConcepts(List.of("dlq", templateName));

            observationRepository.save(dlq);
            log.warn("Stored DLQ entry for template '{}': {}", templateName, errorMsg);
        } catch (Exception e) {
            // Do NOT rethrow — caller (StructuredExtractionService.runProjectExtractions) catches
            // all exceptions and calls storeDLQ(). If we rethrow IllegalStateException, the same
            // catch block would call storeDLQ() again, causing infinite recursion.
            // Instead, log the failure and let the current transaction rollback gracefully.
            log.error("Failed to store DLQ entry for template '{}' (DLQ unavailable, letting transaction rollback): {}",
                templateName, e.getMessage(), e);
        }
    }
}
