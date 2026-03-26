package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.common.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Session lifecycle management service.
 * <p>
 * Responsibilities:
 * - Create/find sessions
 * - Mark session status (active/completed)
 * - Session status queries
 *
 * @author Cortex CE Team
 */
@Service
public class SessionManagementService implements LogHelper {

    private static final Logger log = LoggerFactory.getLogger(SessionManagementService.class);

    @Override
    public Logger getLogger() {
        return log;
    }

    private final SessionRepository sessionRepository;

    public SessionManagementService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Initialize or retrieve a session.
     *
     * @param contentSessionId The session ID from Claude Code
     * @param projectPath Project path for memory isolation
     * @param userPrompt Initial user prompt
     * @return SessionEntity (existing or newly created)
     */
    public SessionEntity initializeSession(String contentSessionId, String projectPath, String userPrompt) {
        return sessionRepository.findByContentSessionId(contentSessionId)
            .orElseGet(() -> createSession(contentSessionId, projectPath, userPrompt));
    }

    /**
     * Ensure session exists (create if not found).
     * Used when session wasn't initialized by SessionStart.
     */
    public SessionEntity ensureSession(String contentSessionId, String projectPath, String userPrompt) {
        return sessionRepository.findByContentSessionId(contentSessionId)
            .orElseGet(() -> {
                logDataIn("Creating session from ensureSession: {} (session was not initialized by SessionStart)", contentSessionId);
                return createSession(contentSessionId, projectPath, userPrompt);
            });
    }

    /**
     * Find session by content session ID.
     */
    public Optional<SessionEntity> findByContentSessionId(String contentSessionId) {
        return sessionRepository.findByContentSessionId(contentSessionId);
    }

    /**
     * Save session entity (for updates like userId assignment).
     */
    public SessionEntity save(SessionEntity session) {
        return sessionRepository.save(session);
    }

    /**
     * Complete a session (sync — marks status only).
     */
    public void completeSession(String contentSessionId) {
        sessionRepository.findByContentSessionId(contentSessionId).ifPresent(session -> {
            session.setStatus("completed");
            session.setCompletedAtEpoch(Instant.now().toEpochMilli());
            session.setCompletedAt(OffsetDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * Complete session and optionally save last assistant message.
     * Used by SummaryGenerationService before generating summary.
     *
     * @param contentSessionId      Session ID
     * @param lastAssistantMessage  Optional last message from transcript
     * @return Session if found, empty otherwise
     */
    public Optional<SessionEntity> completeSessionForSummary(String contentSessionId, String lastAssistantMessage) {
        Optional<SessionEntity> opt = sessionRepository.findByContentSessionId(contentSessionId);
        if (opt.isEmpty()) return Optional.empty();

        SessionEntity session = opt.get();
        if (!"completed".equals(session.getStatus())) {
            session.setStatus("completed");
            session.setCompletedAtEpoch(Instant.now().toEpochMilli());
            session.setCompletedAt(OffsetDateTime.now());
        }
        if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()) {
            session.setLastAssistantMessage(lastAssistantMessage);
            log.debug("Saved lastAssistantMessage for session {}", contentSessionId);
        }
        sessionRepository.save(session);
        return Optional.of(session);
    }

    /**
     * Check if session exists and return it.
     */
    public Optional<SessionEntity> getSessionIfExists(String contentSessionId) {
        return sessionRepository.findByContentSessionId(contentSessionId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private SessionEntity createSession(String contentSessionId, String projectPath, String userPrompt) {
        SessionEntity session = new SessionEntity();
        session.setContentSessionId(contentSessionId);
        session.setProjectPath(projectPath);
        session.setUserPrompt(userPrompt);
        session.setStartedAtEpoch(Instant.now().toEpochMilli());
        session.setStatus("active");

        SessionEntity saved = sessionRepository.save(session);
        if (saved == null) {
            logFailure("Failed to save new session for contentSessionId: {}", contentSessionId);
            throw new RuntimeException("Failed to create session: " + contentSessionId);
        }
        return saved;
    }
}
