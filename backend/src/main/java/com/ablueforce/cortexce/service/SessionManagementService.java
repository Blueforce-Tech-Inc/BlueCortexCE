package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.identity.PlatformSource;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.common.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The single entry point for resolving and creating Conversations.
 *
 * <p>Identity rule (plan §1): a Conversation is identified externally by
 * {@code (platform_source, content_session_id)} and internally by {@link SessionEntity#getId()}.
 * The external id is opaque and case-sensitive; it is never trimmed, lowercased or matched
 * without its platform. Bare-id lookup is therefore deliberately absent from this service —
 * once two platforms may share an id, "find by external id" has no unique answer.
 *
 * <p>Creation is atomic on the database composite unique index. A lost race is resolved by
 * reading the winner, not by catching a generic exception and retrying blind: the winner is
 * the authority on the row's UUID.
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

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    /**
     * Find an existing Conversation for an external reference.
     *
     * @param ref a fully-resolved external identity (platform already canonical)
     */
    @Transactional(readOnly = true)
    public Optional<SessionEntity> find(ExternalSessionRef ref) {
        return sessionRepository.findByPlatformSourceAndContentSessionId(
            ref.platformSource(), ref.contentSessionId());
    }

    /**
     * Legacy-compatible lookup for callers that only carry an external id.
     *
     * <p>Only {@code claude} is searched. Guessing a platform would make a legacy request
     * silently address whichever row happened to exist first, which is exactly the ambiguity
     * this refactor removes.
     */
    @Transactional(readOnly = true)
    public Optional<SessionEntity> findLegacyClaude(String contentSessionId) {
        if (contentSessionId == null) {
            return Optional.empty();
        }
        return sessionRepository.findByPlatformSourceAndContentSessionId(
            PlatformSource.DEFAULT_SOURCE, contentSessionId);
    }

    /** Resolve by internal UUID — the unambiguous key every child table now uses. */
    @Transactional(readOnly = true)
    public Optional<SessionEntity> findById(UUID conversationId) {
        return sessionRepository.findById(conversationId);
    }

    // -------------------------------------------------------------------------
    // Get-or-create
    // -------------------------------------------------------------------------

    /**
     * Return the Conversation for {@code ref}, creating it if absent.
     *
     * <p>Concurrency: the insert relies on {@code ux_sessions_platform_content}. Two
     * simultaneous first-writers both attempt the insert; the loser sees an integrity
     * violation and re-reads, so both callers end up with the same UUID and exactly one row.
     *
     * <p>The project path and user prompt are only used when the row is created. They are
     * never overwritten on an existing Conversation: a resumed session may legitimately pass
     * a different cwd, and silently rewriting it would move historical context.
     */
    @Transactional
    public SessionEntity getOrCreate(ExternalSessionRef ref, String projectPath, String userPrompt) {
        Optional<SessionEntity> existing = find(ref);
        if (existing.isPresent()) {
            return existing.get();
        }

        SessionEntity candidate = new SessionEntity();
        candidate.setPlatformSource(ref.platformSource());
        candidate.setContentSessionId(ref.contentSessionId());
        candidate.setProjectPath(effectiveProjectPath(projectPath));
        candidate.setUserPrompt(userPrompt);
        Instant now = Instant.now();
        // started_at must be set explicitly: JPA inserts every column, so the column DEFAULT
        // never applies and the API would otherwise serialise an empty timestamp.
        candidate.setStartedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        candidate.setStartedAtEpoch(now.toEpochMilli());
        candidate.setStatus("active");

        try {
            return Objects.requireNonNull(sessionRepository.saveAndFlush(candidate),
                "session insert returned null");
        } catch (DataIntegrityViolationException e) {
            // Lost the race against a concurrent creator (or a unique violation on a
            // different constraint). Re-read; the committed row is authoritative.
            Optional<SessionEntity> winner = find(ref);
            if (winner.isPresent()) {
                log.debug("Conversation creation raced; using existing row {}", winner.get().getId());
                return winner.get();
            }
            throw e;
        }
    }

    /**
     * Create a new Conversation row for a given external identity, used by import.
     * Returns the existing row when the identity is already present.
     */
    @Transactional
    public SessionEntity createImported(ExternalSessionRef ref, String projectPath,
                                        String userPrompt, Long startedAtEpoch,
                                        Long completedAtEpoch, String status) {
        Optional<SessionEntity> existing = find(ref);
        if (existing.isPresent()) {
            return existing.get();
        }
        SessionEntity session = new SessionEntity();
        session.setPlatformSource(ref.platformSource());
        session.setContentSessionId(ref.contentSessionId());
        session.setProjectPath(effectiveProjectPath(projectPath));
        session.setUserPrompt(userPrompt);
        Instant started = startedAtEpoch != null ? Instant.ofEpochMilli(startedAtEpoch) : Instant.now();
        session.setStartedAt(OffsetDateTime.ofInstant(started, ZoneOffset.UTC));
        session.setStartedAtEpoch(started.toEpochMilli());
        session.setCompletedAtEpoch(completedAtEpoch);
        session.setStatus(status != null && !status.isBlank() ? status : "active");
        return sessionRepository.saveAndFlush(session);
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    public SessionEntity save(SessionEntity session) {
        return sessionRepository.save(session);
    }

    /** Rename a Conversation's platform — only legal when it is still the legacy default. */
    @Transactional
    public void adoptPlatformIfDefault(SessionEntity session, String requestedPlatform) {
        // Kept for the one legitimate case: an old Claude row whose column was never
        // populated. Any other value is an identity change and must be rejected by callers.
        if (session == null || requestedPlatform == null || requestedPlatform.isBlank()) {
            return;
        }
        String existing = session.getPlatformSource();
        if (existing != null && !existing.isBlank()) {
            return;
        }
        session.setPlatformSource(PlatformSource.normalize(requestedPlatform));
        sessionRepository.save(session);
    }

    /**
     * Complete a Conversation prior to summary generation.
     *
     * @return the updated row, or empty when the Conversation does not exist
     */
    @Transactional
    public Optional<SessionEntity> completeSessionForSummary(UUID conversationId, String lastAssistantMessage) {
        Optional<SessionEntity> opt = sessionRepository.findById(conversationId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        SessionEntity session = opt.get();
        if (!"completed".equals(session.getStatus())) {
            session.setStatus("completed");
            session.setCompletedAtEpoch(Instant.now().toEpochMilli());
            session.setCompletedAt(OffsetDateTime.now());
        }
        if (lastAssistantMessage != null && !lastAssistantMessage.isEmpty()) {
            session.setLastAssistantMessage(lastAssistantMessage);
        }
        return Optional.of(sessionRepository.save(session));
    }

    private static String effectiveProjectPath(String projectPath) {
        return (projectPath != null && !projectPath.isBlank()) ? projectPath : "openclaw";
    }
}