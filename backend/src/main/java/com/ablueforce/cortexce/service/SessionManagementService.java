package com.ablueforce.cortexce.service;

import com.ablueforce.cortexce.entity.SessionEntity;
import com.ablueforce.cortexce.identity.ExternalSessionRef;
import com.ablueforce.cortexce.identity.PlatformSource;
import com.ablueforce.cortexce.repository.SessionRepository;
import com.ablueforce.cortexce.common.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Batch form of {@link #findLegacyClaude(String)}: one query per request, not one per id.
     *
     * <p>A missing id stays absent, which is what the single-id lookup's empty
     * {@link Optional} means; duplicate input ids collapse here and are re-expanded by the
     * caller when the response shape requires one entry per input id.
     */
    @Transactional(readOnly = true)
    public Map<String, SessionEntity> findLegacyClaudeBatch(Collection<String> contentSessionIds) {
        List<String> ids = contentSessionIds == null
            ? List.of()
            : contentSessionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, SessionEntity> found = new LinkedHashMap<>();
        for (SessionEntity session : sessionRepository.findByPlatformSourceAndContentSessionIdIn(
                PlatformSource.DEFAULT_SOURCE, ids)) {
            found.putIfAbsent(session.getContentSessionId(), session);
        }
        return found;
    }

    /**
     * Batch form of {@link #find(ExternalSessionRef)}.
     *
     * <p>Requests are grouped by platform, so a mixed-platform batch issues one query per
     * platform present rather than one per ref.
     */
    @Transactional(readOnly = true)
    public Map<ExternalSessionRef, SessionEntity> findBatch(Collection<ExternalSessionRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> idsByPlatform = new LinkedHashMap<>();
        for (ExternalSessionRef ref : refs) {
            idsByPlatform.computeIfAbsent(ref.platformSource(), key -> new ArrayList<>())
                .add(ref.contentSessionId());
        }
        Map<ExternalSessionRef, SessionEntity> found = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : idsByPlatform.entrySet()) {
            List<String> ids = entry.getValue().stream().distinct().toList();
            for (SessionEntity session : sessionRepository.findByPlatformSourceAndContentSessionIdIn(
                    entry.getKey(), ids)) {
                found.putIfAbsent(
                    new ExternalSessionRef(entry.getKey(), session.getContentSessionId()), session);
            }
        }
        return found;
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
     * <p>Concurrency: an insert-on-conflict operation targets the exact composite identity.
     * Concurrent first-writers therefore converge on the canonical row without aborting the
     * losing PostgreSQL transaction.
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

        Instant now = Instant.now();
        OffsetDateTime startedAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        sessionRepository.insertIfIdentityAbsent(ref.contentSessionId(), effectiveProjectPath(projectPath),
            userPrompt, null, startedAt, now.toEpochMilli(), null, null, "active", ref.platformSource());
        return find(ref).orElseThrow(() -> new IllegalStateException(
            "conversation insert did not produce the requested identity: " + ref.logKey()));
    }

    /**
     * Create a new Conversation row for a given external identity, used by import.
     * Returns the existing row when the identity is already present.
     */
    @Transactional
    public SessionEntity createImported(ExternalSessionRef ref, String projectPath,
                                        String userPrompt, String lastAssistantMessage,
                                        Long startedAtEpoch,
                                        Long completedAtEpoch, String status) {
        Optional<SessionEntity> existing = find(ref);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant started = startedAtEpoch != null ? Instant.ofEpochMilli(startedAtEpoch) : Instant.now();
        OffsetDateTime completedAt = completedAtEpoch == null ? null
            : OffsetDateTime.ofInstant(Instant.ofEpochMilli(completedAtEpoch), ZoneOffset.UTC);
        sessionRepository.insertIfIdentityAbsent(ref.contentSessionId(), effectiveProjectPath(projectPath),
            userPrompt, lastAssistantMessage, OffsetDateTime.ofInstant(started, ZoneOffset.UTC), started.toEpochMilli(),
            completedAt, completedAtEpoch,
            status != null && !status.isBlank() ? status : "active", ref.platformSource());
        return find(ref).orElseThrow(() -> new IllegalStateException(
            "import insert did not produce the requested identity: " + ref.logKey()));
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

    /**
     * Store generated context on the project's active Conversation.
     *
     * <p>Best-effort by design: a failed cache write must not fail session start,
     * because the context can be regenerated. The caller no longer needs the
     * repository to make that distinction.
     */
    public void cacheContextForProject(String projectPath, String context) {
        List<SessionEntity> sessions = sessionRepository.findByProjectPathAndStatus(projectPath, "active");
        if (sessions.isEmpty()) {
            return;
        }
        SessionEntity activeSession = sessions.get(0);
        activeSession.setCachedContext(context);
        activeSession.setContextRefreshedAtEpoch(System.currentTimeMillis());
        try {
            sessionRepository.save(activeSession);
        } catch (RuntimeException e) {
            log.warn("Failed to cache context for project {}: {}", projectPath, e.getMessage());
        }
    }

    private static String effectiveProjectPath(String projectPath) {
        return (projectPath != null && !projectPath.isBlank()) ? projectPath : "openclaw";
    }
}
